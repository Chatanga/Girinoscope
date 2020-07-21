package org.hihan.girinoscope.comm;

import com.fazecast.jSerialComm.SerialPort;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Reading operations are semi-interruptible here. As long as nothing as been
 * read, it can be interrupted, but once something has been read, it continues
 * up to the line / buffer completion. This behavior is here to avoid to stop
 * reading a bunch of data sent by the Girino. It does it fast enough not to
 * bother us. The only delay we want to interrupt is when nothing is coming, per
 * instance when we wait for the trigger to happen. A crossover can still occur
 * - the trigger happening the same time the user cancel the operation - but it
 * is not likely to happen and the Girino doesn’t support a complex enough
 * protocol to prevent this kind of problem anyway. On the other hand, the
 * consequence is not fatal. We will read garbage the next time, display some
 * error to the user and move along.
 */
public class Serial implements Closeable {

    private static final Logger LOGGER = Logger.getLogger(Serial.class.getName());

    /*
     * I can’t remember why I try to filter the ports available. Any serial
     * port should work here and there is no need to check if it is a USB
     * adapter or something else.
     */
    private static final Pattern[] ACCEPTABLE_PORT_NAMES = {
        //
        Pattern.compile(".*tty\\.usbserial-.+"), // Mac OS X
        Pattern.compile(".*cu\\.wchusbserial.+"), // Mac OS X
        Pattern.compile(".*tty\\.usbmodem.+"), // Mac OS X
        Pattern.compile(".*ttyACM\\d+"), // Linux "modem"
        Pattern.compile(".*ttyUSB\\d+"), // Linux USB to serial adapter
        Pattern.compile(".*rfcomm\\d+"), // Linux Bluetooth
        Pattern.compile(".*COM\\d+"), // Windows
    };

    /*
     * Milliseconds to block while waiting for port open.
     */
    private static final int TIME_OUT = 2000;

    /*
     * Default bits per second for COM port.
     */
    private static final int DATA_RATE = 115200;

    /*
     * Milliseconds to wait when no input is available.
     */
    private static final int READ_DELAY = 200;

    private SerialPort port;

    private InputStream input;

    private OutputStream output;

    public static List<SerialPort> enumeratePorts() {
        List<SerialPort> ports = new LinkedList<>();

        for (SerialPort port : SerialPort.getCommPorts()) {
            String portName = port.getSystemPortName();
            for (Pattern acceptablePortName : ACCEPTABLE_PORT_NAMES) {
                if (acceptablePortName.matcher(portName).matches()) {
                    ports.add(port);
                    break;
                }
            }
        }

        return ports;
    }

    public Serial(SerialPort port) throws IOException {
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, TIME_OUT, TIME_OUT);
        port.setComPortParameters(DATA_RATE, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        port.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED);

        /*
         * The 'openPort' method is interruptible, but will end with an inappropriate
         * 'printStackTrace' instead of rethrowing an interrupt itself or, better,
         * not catching the interrupt.
         */
        if (port.openPort()) {
            this.port = port;

            output = port.getOutputStream();
            input = port.getInputStream();
        } else {
            throw new IOException("Cannot open serial port.");
        }
    }

    @Override
    public void close() throws IOException {
        if (port != null) {
            try {
                output.flush();
                output.close();
                input.close();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "When flushing output before closing serial.", e);
            }
            port.closePort();
            port = null;
        }
    }

    public String readLine() throws IOException, InterruptedException {
        StringBuilder line = new StringBuilder();
        int length = 0;
        try {
            while (true) {
                int c;
                if ((input.available() > 0 || line.length() > 0) && (c = input.read()) >= 0) {
                    line.append((char) c);
                    ++length;
                    boolean eol = length >= 2 && line.charAt(length - 2) == '\r' && line.charAt(length - 1) == '\n';
                    if (eol) {
                        line.setLength(length - 2);
                        break;
                    }
                } else {
                    /*
                     * Sleeping here allows us to be interrupted (the serial
                     * input is not interruptible itself).
                     */
                    Thread.sleep(READ_DELAY);
                }
            }
        } catch (InterruptedException e) {
            LOGGER.log(Level.FINE, "Read aborted");
            throw e;
        }
        LOGGER.log(Level.FINE, "< ({0})", line);
        return line.toString();
    }

    public int readBytes(byte[] buffer) throws IOException, InterruptedException {
        int offset = 0;
        try {
            while (offset < buffer.length) {
                if (input.available() > 0 || offset > 0) {
                    int size = input.read(buffer, offset, buffer.length - offset);
                    if (size < 0) {
                        break;
                    }
                    offset += size;
                } else {
                    /*
                     * Sleeping here allows us to be interrupted (the serial
                     * input is not interruptible itself).
                     */
                    Thread.sleep(READ_DELAY);
                }
            }
        } catch (InterruptedException e) {
            LOGGER.log(Level.FINE, "Read aborted");
            throw e;
        }
        LOGGER.log(Level.FINE, "< {0} byte(s)", offset);
        return offset;
    }

    public void writeLine(String line) throws IOException {
        for (int i = 0; i < line.length(); ++i) {
            output.write(line.charAt(i));
        }
        output.flush();
        LOGGER.log(Level.FINE, "> ({0})", line);
    }
}
