package org.hihan.girinoscope.comm;

import com.fazecast.jSerialComm.SerialPort;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Girino {

    public enum Parameter {

        BUFFER_SIZE(null, true), //
        BAUD_RATE(null, true), //
        PRESCALER("p", true), //
        VOLTAGE_REFERENCE("r", false), //
        TRIGGER_EVENT("e", true), //
        WAIT_DURATION("w", true), //
        THRESHOLD("t", true);

        private final String command;
        private final boolean readable;

        Parameter(String command, boolean readable) {
            this.command = command;
            this.readable = readable;
        }

        public @NotNull
        String getIdentifier() {
            if (this == WAIT_DURATION) {
                return "waitDuration";
            } else {
                return name().toLowerCase().replace('_', ' ');
            }
        }

        @NotNull
        public String getDescription() {
            return name().charAt(0) + name().substring(1).toLowerCase().replace('_', ' ');
        }

        @Nullable
        public static Parameter findByDescription(String description) {
            for (Parameter parameter : values()) {
                if (parameter.getDescription().equals(description)) {
                    return parameter;
                }
            }
            return null;
        }

        @NotNull
        private static Map.Entry<Parameter, Integer> read(Serial serial) throws IOException, InterruptedException {
            String data = serial.readLine();
            if (READY_MESSAGE.equals(data)) {
                data = serial.readLine();
            }
            String[] items = data.split(":");
            String name = items[0].trim();
            int value = Integer.parseInt(items[1].trim());
            Parameter parameter = Parameter.findByDescription(name);
            return new AbstractMap.SimpleEntry<>(parameter, value);
        }

        private int apply(Serial serial, int newValue) throws IOException, InterruptedException {
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
            double clockCycleCountPerConversion = 13;
            frequency = BASE_FREQUENCY / value / clockCycleCountPerConversion;
            timeframe = FRAME_SIZE / frequency;
            tooFast = n < 5;
            reallyTooFast = n < 3;
            description = String.format("%.1f kHz / %.1f ms", frequency / 1000, timeframe * 1000);
        }

        public static List<PrescalerInfo> values() {
            List<PrescalerInfo> infos = new LinkedList<>();
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

        TriggerEventMode(int value, String description) {
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

        VoltageReference(int value, String description) {
            this.value = value;
            this.description = description;
        }
    }

    /**
     * The size of a data frame.
     */
    public static final int FRAME_SIZE = 1280;

    /**
     * The Arduino clock frequency in milliseconds.
     */
    private static final int BASE_FREQUENCY = 16_000_000;

    /**
     * Milliseconds to wait once a new connection has been etablished.
     */
    private static final int SETUP_DELAY_ON_RESET = 2000;

    private static final String READY_MESSAGE = "Girino ready";

    private static final String START_ACQUIRING_COMMAND = "s";

    private static final String STOP_ACQUIRING_COMMAND = "S";

    private static final String DUMP_COMMAND = "d";

    private Serial serial;

    private SerialPort port;

    private final Map<Parameter, Integer> parameters = new HashMap<>();

    public static Map<Parameter, Integer> getDefaultParameters(Map<Parameter, Integer> parameters) {
        parameters.put(Parameter.BUFFER_SIZE, FRAME_SIZE);
        parameters.put(Parameter.PRESCALER, 32);
        parameters.put(Parameter.VOLTAGE_REFERENCE, VoltageReference.AVCC.value);
        parameters.put(Parameter.TRIGGER_EVENT, TriggerEventMode.TOGGLE.value);
        parameters.put(Parameter.WAIT_DURATION, FRAME_SIZE - 32);
        parameters.put(Parameter.THRESHOLD, 150);
        return parameters;
    }

    @Contract("null -> fail")
    private void connect(SerialPort newPort) throws IOException, InterruptedException {
        if (newPort != null) {
            if (serial == null || !Objects.equals(port, newPort)) {
                port = newPort;
                if (serial != null) {
                    disconnect();
                }

                serial = new Serial(port);
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
                } catch (InterruptedException e) {
                    disconnect();
                }

                readParameters();
            }
        } else {
            throw new IllegalArgumentException("No serial port (see README.md file)");
        }
    }

    public void connect(SerialPort newPort, Map<Parameter, Integer> newParameters)
            throws Exception {
        connect(newPort);
        if (serial != null) {
            applyParameters(newParameters);
        }
    }

    public void disconnect() throws IOException {
        if (serial != null) {
            serial.close();
            serial = null;
        }
    }

    private void readParameters() throws IOException, InterruptedException {
        if (serial != null) {
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
        } else {
            throw new IllegalStateException("No serial connection");
        }
    }

    /**
     * @throws IOException Provided parameters shall be verified by the caller
     * since some parameters could have been set back to a different value than
     * asked.
     */
    private void applyParameters(Map<Parameter, Integer> newParameters) throws IOException, InterruptedException {
        if (serial != null) {
            for (Map.Entry<Parameter, Integer> entry : newParameters.entrySet()) {
                Parameter parameter = entry.getKey();
                Integer newValue = entry.getValue();

                // We only update modified parameters.
                if (!Objects.equals(newValue, parameters.get(parameter))) {
                    int returnedValue = parameter.apply(serial, newValue);
                    parameters.put(parameter, returnedValue);
                    if (!Objects.equals(newValue, parameters.get(parameter))) {
                        throw new IOException("Change has been rejected for parameter "
                                + parameter.getDescription() + ": " + newValue + " =/= " + returnedValue);
                    }
                }
            }
        } else {
            throw new IllegalStateException("No serial connection");
        }
    }

    public byte[] acquireData() throws Exception {
        if (serial != null) {
            serial.writeLine(START_ACQUIRING_COMMAND);
            /*
             * Note that the Girino reset its buffer (with zeros), meaning we won’t
             * catch a lot of the signal before the trigger if it happens too fast.
             */
            try {
                byte[] buffer = new byte[FRAME_SIZE];
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
        } else {
            throw new IllegalStateException("No serial connection");
        }
    }
}
