package org.hihan.girinoscope.comm;

import gnu.io.CommPortIdentifier;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class Girino {

    public enum Parameter {
        BUFFER_SIZE(null, true), //
        BAUD_RATE(null, true), //
        PRESCALER("p", true), //
        VOLTAGE_REFERENCE("r", false), // Added by me.
        TRIGGER_EVENT("e", true), //
        WAIT_DURATION("w", true), //
        THRESHOLD("t", true);

        private String command;

        public boolean readable;

        Parameter(String command, boolean readable) {
            this.command = command;
            this.readable = readable;
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
    }

    public static class PrescalerInfo {

        public final int value;
        public final double frequency;
        public final double timeframe;
        public final String description;

        private PrescalerInfo(int n) {
            value = (int) Math.pow(2, n);
            frequency = 625.0 / value;
            timeframe = 1280 / frequency;
            String warning = n < 5 ? " (Too high for LM324N, use TL084 instead)" : "";
            description = String.format("%.0f kHz / %.0f ms%s", frequency, timeframe, warning);
        }
    }

    public static List<PrescalerInfo> getPrescalerInfos() {
        List<PrescalerInfo> infos = new LinkedList<PrescalerInfo>();
        for (int i = 2; i < 8; ++i) {
            infos.add(new PrescalerInfo(i));
        }
        return infos;
    }

    private static final String READY_MESSAGE = "Girino ready";

    private static final String START_ACQUIRING_COMMAND = "s";

    private static final String STOP_ACQUIRING_COMMAND = "S";

    private static final String DUMP_COMMAND = "d";

    private Serial serial;

    private CommPortIdentifier portId;

    private Map<Parameter, Integer> parameters = new HashMap<Parameter, Integer>();

    private void connect(CommPortIdentifier newPortId) throws Exception {
        if (newPortId != null) {
            if (!same(portId, newPortId)) {
                portId = newPortId;
                if (serial != null) {
                    disconnect();
                }

                serial = new Serial(portId);

                /*
                 * Note that the USB to serial adapter is usually configured to
                 * reset the AVR each time a connection is etablish.
                 */
                Thread.sleep(2000);

                String data;
                do {
                    data = serial.readLine();
                } while (!data.endsWith(READY_MESSAGE));

                readParameters();
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
        serial.writeLine(DUMP_COMMAND);
        int readableParameterCount = 0;
        for (Parameter parameter : Parameter.values()) {
            if (parameter.readable) {
                ++readableParameterCount;
            }
        }
        for (int i = 0; i < readableParameterCount; ++i) {
            String data = serial.readLine();
            if (READY_MESSAGE.equals(data)) {
                data = serial.readLine();
            }
            String[] items = data.split(":");
            String name = items[0].trim();
            int value = Integer.parseInt(items[1].trim());
            Parameter parameter = Parameter.findByDescription(name);
            if (parameter != null) {
                parameters.put(parameter, value);
            }
        }
    }

    private void applyParameters(Map<Parameter, Integer> newParameters) throws IOException {
        for (Map.Entry<Parameter, Integer> entry : newParameters.entrySet()) {
            Parameter parameter = entry.getKey();
            if (!same(entry.getValue(), parameters.get(parameter))) {
                if (parameter.command != null) {
                    serial.writeLine(parameter.command + entry.getValue());
                    String data = serial.readLine();
                    String[] items = data.split(":");
                    if (items.length > 1) {
                        String name = items[0].trim();
                        if (name.equals(String.format("Setting %s to", parameter.getDescription().toLowerCase()))) {
                            int value = Integer.parseInt(items[1].trim());
                            parameters.put(parameter, value);
                            if (!same(entry.getValue(), parameters.get(parameter))) {
                                throw new IOException("Change has been rejected for parameter "
                                        + parameter.getDescription());
                            }
                        } else {
                            throw new IOException("Not matching returned parameter " + parameter.getDescription());
                        }
                    } else {
                        throw new IOException("Unknown parameter " + parameter.getDescription());
                    }
                } else {
                    throw new IllegalArgumentException("Read only parameter " + parameter.getDescription());
                }
            }
        }
    }

    public void etablishConnection(final CommPortIdentifier newPortId, final Map<Parameter, Integer> newParameters)
            throws Exception {
        connect(newPortId);
        applyParameters(newParameters);
    }

    public byte[] acquireData() throws Exception {
        serial.writeLine(START_ACQUIRING_COMMAND);
        byte[] buffer = new byte[1280];
        int size = serial.readBytes(buffer);
        serial.writeLine(STOP_ACQUIRING_COMMAND);
        return size == buffer.length ? buffer : null;
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
