package org.hihan.girinoscope.comm;

import gnu.io.CommPortIdentifier;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class Girino {

    public enum Parameter {
        BUFFER_SIZE(null, true), //
        BAUD_RATE(null, true), //
        PRESCALER("p", true), //
        VOLTAGE_REFERENCE("r", false), //
        TRIGGER_EVENT("e", true), //
        WAIT_DURATION("w", true), //
        THRESHOLD("t", true);

        private String command;
        private boolean readable;

        Parameter(String command, boolean readable) {
            this.command = command;
            this.readable = readable;
        }

        public String getIdentifier() {
            if (this == WAIT_DURATION) {
                return "waitDuration";
            } else {
                return name().toLowerCase().replace('_', ' ');
            }
        }

        public String getDescription() {
            return name().charAt(0) + name().substring(1).toLowerCase().replace('_', ' ');
        }

        public static Parameter findByDescription(String description) {
            for (Parameter parameter : values()) {
                if (parameter.getDescription().equals(description)) {
                    return parameter;
                }
            }
            return null;
        }

        private static Map.Entry<Parameter, Integer> read(Serial serial) throws IOException {
            String data = serial.readLine();
            if (READY_MESSAGE.equals(data)) {
                data = serial.readLine();
            }
            String[] items = data.split(":");
            String name = items[0].trim();
            int value = Integer.parseInt(items[1].trim());
            Parameter parameter = Parameter.findByDescription(name);
            return new AbstractMap.SimpleEntry<Parameter, Integer>(parameter, value);
        }

        private int apply(Serial serial, int newValue) throws IOException {
            if (command != null) {
                serial.writeLine(command + newValue);
                String data = serial.readLine();
                String[] items = data.split(":");
                if (items.length > 1) {
                    String message = items[0].trim();
                    String identifier = getIdentifier();
                    if (message.equals(String.format("Setting %s to", identifier))) {
                        return Integer.parseInt(items[1].trim());
                    } else {
                        throw new IOException("Not matching returned parameter " + identifier);
                    }
                } else {
                    throw new IOException("Unknown parameter " + getDescription());
                }
            } else {
                throw new IllegalArgumentException("Read only parameter " + getDescription());
            }
        }
    }

    public static class PrescalerInfo {

        public final int value;
        public final double frequency;
        public final double timeframe;
        public final String description;
        public final boolean tooFast;
        public final boolean reallyTooFast;

        private PrescalerInfo(int n) {
            value = (int) Math.pow(2, n);
            double baseFrequency = 16 * 1000 * 1000;
            double clockCycleCountPerConversion = 13;
            frequency = baseFrequency / value / clockCycleCountPerConversion;
            timeframe = 1280 / frequency;
            tooFast = n < 5;
            reallyTooFast = n < 3;
            description = String.format("%.1f kHz / %.1f ms", frequency / 1000, timeframe * 1000);
        }

        public static List<PrescalerInfo> values() {
            List<PrescalerInfo> infos = new LinkedList<PrescalerInfo>();
            for (int i = 2; i < 8; ++i) {
                infos.add(new PrescalerInfo(i));
            }
            return infos;
        }
    }

    public enum TriggerEventMode {

        TOGGLE(0, "Toggle"), //
        FALLING_EDGE(2, "Falling edge"), //
        RISING_EDGE(3, "Rising edge");

        public int value;
        public String description;

        private TriggerEventMode(int value, String description) {
            this.value = value;
            this.description = description;
        }
    }

    public enum VoltageReference {

        AREF(0, "AREF, Internal Vref turned off"), //
        AVCC(1, "AVCC with external capacitor at AREF pin"), //
        /*
         * The value is misleading since it sets [REFS1, REFS0] to 11 (10 is
         * reserved). At least, the trigger event mode always uses the [ACI1,
         * ACI0] value (hence the gap since 2 is also a reserved value).
         */
        INTERNAL(2, "Internal 1.1V Vref with external capacitor at AREF pin");

        public int value;
        public String description;

        private VoltageReference(int value, String description) {
            this.value = value;
            this.description = description;
        }
    }

    /** Milliseconds to wait once a new connection has been etablished. */
    private static final int SETUP_DELAY_ON_RESET = 5000;

    private static final String READY_MESSAGE = "Girino ready";

    private static final String START_ACQUIRING_COMMAND = "s";

    private static final String STOP_ACQUIRING_COMMAND = "S";

    private static final String DUMP_COMMAND = "d";

    private Serial serial;

    private CommPortIdentifier portId;

    private Map<Parameter, Integer> parameters = new HashMap<Parameter, Integer>();

    public static Map<Parameter, Integer> getDefaultParameters(Map<Parameter, Integer> parameters) {
        parameters.put(Parameter.BUFFER_SIZE, 1280);
        parameters.put(Parameter.PRESCALER, 32);
        parameters.put(Parameter.VOLTAGE_REFERENCE, VoltageReference.AVCC.value);
        parameters.put(Parameter.TRIGGER_EVENT, TriggerEventMode.TOGGLE.value);
        parameters.put(Parameter.WAIT_DURATION, 1280 - 32);
        parameters.put(Parameter.THRESHOLD, 150);
        return parameters;
    }

    private void connect(CommPortIdentifier newPortId) throws Exception {
        if (newPortId != null) {
            if (serial == null || !same(portId, newPortId)) {
                portId = newPortId;
                if (serial != null) {
                    disconnect();
                }

                serial = new Serial(portId);
                try {
                    /*
                     * Note that the USB to serial adapter is usually configured
                     * to reset the AVR each time a connection is etablish. The
                     * delay here is to give some time to the controller to set
                     * itself up.
                     */
                    Thread.sleep(SETUP_DELAY_ON_RESET);

                    String data;
                    do {
                        data = serial.readLine();
                    } while (!data.endsWith(READY_MESSAGE));

                    readParameters();
                } catch (InterruptedException e) {
                    disconnect();
                }
            }
        } else {
            throw new IllegalArgumentException("No serial port");
        }
    }

    public void disconnect() throws IOException {
        if (serial != null) {
            serial.close();
            serial = null;
        }
    }

    private void readParameters() throws IOException {
        int readableParameterCount = 0;
        for (Parameter parameter : Parameter.values()) {
            if (parameter.readable) {
                ++readableParameterCount;
            }
        }

        serial.writeLine(DUMP_COMMAND);
        for (int i = 0; i < readableParameterCount; ++i) {
            Map.Entry<Parameter, Integer> entry = Parameter.read(serial);
            if (entry.getKey() != null) {
                parameters.put(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * @throws IOException
     *             Provided parameters shall be verified by the caller since
     *             some parameters could have been set back to a different value
     *             than asked.
     */
    private void applyParameters(Map<Parameter, Integer> newParameters) throws IOException {
        for (Map.Entry<Parameter, Integer> entry : newParameters.entrySet()) {
            Parameter parameter = entry.getKey();
            Integer newValue = entry.getValue();

            // We only update modified parameters.
            if (!same(newValue, parameters.get(parameter))) {
                int returnedValue = parameter.apply(serial, newValue);
                parameters.put(parameter, returnedValue);
                if (!same(newValue, parameters.get(parameter))) {
                    throw new IOException("Change has been rejected for parameter " + parameter.getDescription() + ": "
                            + newValue + " =/= " + returnedValue);
                }
            }
        }
    }

    public void setConnection(final CommPortIdentifier newPortId, final Map<Parameter, Integer> newParameters)
            throws Exception {
        connect(newPortId);
        if (serial != null) {
            applyParameters(newParameters);
        }
    }

    public byte[] acquireData() throws Exception {
        serial.writeLine(START_ACQUIRING_COMMAND);
        /*
         * Note that the Girino reset its buffer (with zeros), meaning we won’t
         * catch a lot of the signal before the trigger if it happens too fast.
         */
        try {
            byte[] buffer = new byte[1280];
            int size = serial.readBytes(buffer);
            return size == buffer.length ? buffer : null;
        } finally {
            /*
             * We can only acquire a single buffer and need to stop / start to
             * get the next one. That’s how the Girino code works, but it was
             * probably a bit different during its development. In practise,
             * this 'stop' is only required when we cancel a trigger waiting.
             */
            serial.writeLine(STOP_ACQUIRING_COMMAND);
        }
    }

    private static boolean same(Object o1, Object o2) {
        if (o1 == o2) {
            return true;
        } else if (o1 == null || o2 == null) {
            return false;
        } else {
            return o1.equals(o2);
        }
    }
}
