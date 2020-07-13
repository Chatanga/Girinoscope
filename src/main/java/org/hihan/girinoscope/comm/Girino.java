package org.hihan.girinoscope.comm;

import com.fazecast.jSerialComm.SerialPort;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Girino {

    public enum Parameter {

        BUFFER_SIZE(null),
        BAUD_RATE(null),
        PRESCALER("p"),
        VOLTAGE_REFERENCE("r"),
        TRIGGER_EVENT("e"),
        WAIT_DURATION("w"),
        THRESHOLD("t");

        private final String command;

        Parameter(String command) {
            this.command = command;
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

        private static Map.Entry<Parameter, Integer> read(Serial serial, Device device) throws IOException, InterruptedException {
            String data = serial.readLine();
            if (device.getReadyMessage().equals(data)) {
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
        public final boolean tooFast;
        public final boolean reallyTooFast;

        public PrescalerInfo(
                int value,
                double frequency,
                double timeframe,
                boolean tooFast,
                boolean reallyTooFast) {
            this.value = value;
            this.frequency = frequency;
            this.timeframe = timeframe;
            this.tooFast = tooFast;
            this.reallyTooFast = reallyTooFast;
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

    private static final String START_ACQUIRING_COMMAND = "s";

    private static final String STOP_ACQUIRING_COMMAND = "S";

    private static final String DUMP_COMMAND = "d";

    private Serial serial;

    private SerialPort port;

    private Device device = Device.createClassic();

    private final Map<Parameter, Integer> parameters = new HashMap<>();

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
                     * The USB to serial adapter is expected to reset the AVR
                     * each time a connection is etablished. The delay here is to
                     * give some time to the controller to set itself up. Since
                     * the Girino protocol only output its signature at startup,
                     * a lack of response could be an inappropriate serial adapter
                     * without the DTR/RTS wire (ie. it has only 4 wires) used to
                     * force a reset. It won’t be the case with an Arduino, but
                     * if you have built your device from scratch, it could.
                     */
                    Thread.sleep(device.getSetupDelayOnReset());

                    String data;
                    do {
                        data = serial.readLine();
                    } while (!data.endsWith(device.getReadyMessage()));
                } catch (InterruptedException e) {
                    disconnect();
                }

                readParameters();
            }
        } else {
            throw new IllegalArgumentException("No serial port (see README.md file)");
        }
    }

    public void connect(Device device, SerialPort newPort, Map<Parameter, Integer> newParameters)
            throws Exception {
        this.device = Objects.requireNonNull(device);
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
            serial.writeLine(DUMP_COMMAND);
            for (Parameter parameter : Parameter.values()) {
                if (device.isReadable(parameter)) {
                    Map.Entry<Parameter, Integer> entry = Parameter.read(serial, device);
                    if (entry.getKey() != null) {
                        parameters.put(entry.getKey(), entry.getValue());
                    }
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
                    if (device.isWritable(parameter)) {
                        int returnedValue = parameter.apply(serial, newValue);
                        parameters.put(parameter, returnedValue);
                        if (!Objects.equals(newValue, parameters.get(parameter))) {
                            throw new IOException("Change has been rejected for parameter "
                                    + parameter.getDescription() + ": " + newValue + " =/= " + returnedValue);
                        }
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
                FrameFormat frameFormat = device.getFrameFormat();
                byte[] buffer = new byte[frameFormat.sampleCount * frameFormat.sampleSizeInBit];
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
