package org.hihan.girinoscope.ui;

import com.fazecast.jSerialComm.SerialPort;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.WindowConstants;
import org.hihan.girinoscope.comm.Device;
import org.hihan.girinoscope.comm.Girino;
import org.hihan.girinoscope.comm.Girino.Parameter;
import org.hihan.girinoscope.comm.Girino.PrescalerInfo;
import org.hihan.girinoscope.comm.Girino.TriggerEventMode;
import org.hihan.girinoscope.comm.Girino.VoltageReference;
import org.hihan.girinoscope.comm.Serial;

@SuppressWarnings("serial")
public class UI extends JFrame {

    private static final Logger LOGGER = Logger.getLogger(UI.class.getName());

    public static void main(String[] args) throws Exception {
        Logger rootLogger = Logger.getLogger("org.hihan.girinoscope");
        rootLogger.setLevel(Level.INFO);

        for (String arg : args) {
            if ("-debug".equals(arg)) {
                ConsoleHandler handler = new ConsoleHandler();
                handler.setFormatter(new SimpleFormatter());
                handler.setLevel(Level.ALL);
                rootLogger.addHandler(handler);
            }
        }

        JFrame.setDefaultLookAndFeelDecorated(true);
        JDialog.setDefaultLookAndFeelDecorated(true);
        try {
            String[] allLafs = {
                "javax.swing.plaf.nimbus.NimbusLookAndFeel",
                "com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel",
                UIManager.getSystemLookAndFeelClassName()
            };
            for (String laf : allLafs) {
                if (setLookAndFeelIfAvailable(laf)) {
                    break;
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "When setting the look and feel.", e);
        }

        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                JFrame frame = new UI();
                frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            }
        });
    }

    private static boolean setLookAndFeelIfAvailable(String className)
            throws InstantiationException, IllegalAccessException, UnsupportedLookAndFeelException {
        try {
            if (UI.class.getClassLoader().loadClass(className) != null) {
                UIManager.setLookAndFeel(className);
                return true;
            } else {
                return false;
            }
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private final Settings settings = new Settings();

    /*
     * The Girino protocol interface.
     */
    private final Girino girino = new Girino();

    /*
     * The selected device on which the Girino firmware is running (could be
     * different from the one currently configured for the Girino).
     */
    private Device device;

    /*
     * The currently selected serial port used to connect to the Girino
     * hardware.
     */
    private SerialPort port;

    /*
     * The edited Girino settings (could be different from the ones uploaded to
     * the Girino hardware).
     */
    private Map<Parameter, Integer> parameters;

    /*
     * Helper class storing the attributes of the Y axis in order to create new
     * instances.
     */
    private Axis.Builder yAxisBuilder = new Axis.Builder();

    private GraphPane graphPane;

    private final StatusBar statusBar;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private DataAcquisitionTask currentDataAcquisitionTask;

    private static class ByteArray {

        private final byte[] bytes;

        public ByteArray(byte[] bytes) {
            this.bytes = bytes;
        }
    }

    /*
     * All the communication with the Girino interface is done asynchrously
     * through this class (save the disposal).
     */
    private class DataAcquisitionTask extends SwingWorker<Void, ByteArray> {

        private Device frozenDevice;

        private SerialPort frozenPort;

        private final Map<Parameter, Integer> frozenParameters = new HashMap<>();

        private final boolean repeated;

        public DataAcquisitionTask(boolean repeated) {
            this.repeated = repeated;
            startAcquiringAction.setEnabled(false);
            startAcquiringInLoopAction.setEnabled(false);
            stopAcquiringAction.setEnabled(true);
            exportLastFrameAction.setEnabled(true);
        }

        @Override
        protected Void doInBackground() throws Exception {
            do {
                updateConnection();
                acquireData();
            } while (repeated && !isCancelled());
            return null;
        }

        private void updateConnection() throws Exception {
            synchronized (UI.this) {
                frozenDevice = device;
                frozenPort = port;
                frozenParameters.putAll(parameters);
            }

            setStatus("blue", "Contacting Girino on %s...", frozenPort.getSystemPortName());

            Future<Void> connection = executor.submit(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    girino.connect(frozenDevice, frozenPort, frozenParameters);
                    return null;
                }
            });
            try {
                connection.get(15, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                connection.cancel(true);
                throw new TimeoutException("No Girino detected on " + frozenPort.getSystemPortName());
            } catch (InterruptedException e) {
                connection.cancel(true);
                throw e;
            }
        }

        private void acquireData() throws Exception {
            setStatus("blue", "Acquiring data from %s...", frozenPort.getSystemPortName());
            Future<byte[]> acquisition = null;
            boolean terminated;
            do {
                boolean updateConnection;
                synchronized (UI.this) {
                    parameters.put(Parameter.THRESHOLD, graphPane.getThreshold());
                    parameters.put(Parameter.WAIT_DURATION, graphPane.getWaitDuration());
                    updateConnection
                            = frozenDevice != device
                            || frozenPort != port
                            || !calculateChanges(frozenParameters).isEmpty();
                }
                if (updateConnection) {
                    if (acquisition != null) {
                        acquisition.cancel(true);
                    }
                    terminated = true;
                } else {
                    if (acquisition == null) {
                        acquisition = executor.submit(new Callable<byte[]>() {
                            @Override
                            public byte[] call() throws Exception {
                                return girino.acquireData();
                            }
                        });
                    }
                    try {
                        byte[] buffer = acquisition.get(1, TimeUnit.SECONDS);
                        if (buffer != null) {
                            publish(new ByteArray(buffer));
                            acquisition = null;
                            terminated = !repeated;
                        } else {
                            terminated = true;
                        }
                    } catch (TimeoutException e) {
                        // Just to wake up regularly.
                        terminated = false;
                    } catch (InterruptedException e) {
                        acquisition.cancel(true);
                        throw e;
                    }
                }
            } while (!terminated);
        }

        @Override
        protected void process(List<ByteArray> byteArrays) {
            LOGGER.log(Level.FINE, "{0} data buffer(s) to display.", byteArrays.size());
            graphPane.setData(byteArrays.get(byteArrays.size() - 1).bytes);
        }

        @Override
        protected void done() {
            startAcquiringAction.setEnabled(true);
            startAcquiringInLoopAction.setEnabled(true);
            stopAcquiringAction.setEnabled(false);
            exportLastFrameAction.setEnabled(true);
            try {
                if (!isCancelled()) {
                    get();
                }
                setStatus("blue", "Done acquiring data from %s.", frozenPort.getSystemPortName());
            } catch (ExecutionException e) {
                LOGGER.log(Level.WARNING, "When acquiring data.", e);
                setStatus("red", e.getCause().getMessage());
            } catch (Exception e) {
                setStatus("red", e.getMessage());
            }
        }
    }

    private final Action exportLastFrameAction = new AbstractAction("Export last frame", Icon.get("document-save.png")) {
        {
            putValue(Action.SHORT_DESCRIPTION, "Export the last time frame to CSV.");
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            DateFormat format = new SimpleDateFormat("yyyy_MM_dd-HH_mm");
            fileChooser.setSelectedFile(new File("frame-" + format.format(new Date()) + ".csv"));
            if (fileChooser.showSaveDialog(UI.this) == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                int[] value = graphPane.getValues();
                BufferedWriter writer = null;
                try {
                    writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8));
                    for (int i = 0; i < value.length; ++i) {
                        writer.write(String.format("%d;%d", i, value[i]));
                        writer.newLine();
                    }
                } catch (IOException e) {
                    setStatus("red", e.getMessage());
                } finally {
                    if (writer != null) {
                        try {
                            writer.close();
                        } catch (IOException e) {
                            setStatus("red", e.getMessage());
                        }
                    }
                }
            }
        }
    };

    private final Action stopAcquiringAction = new AbstractAction("Stop acquiring", Icon.get("media-playback-stop.png")) {
        {
            putValue(Action.SHORT_DESCRIPTION, "Stop acquiring data from Girino.");
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            currentDataAcquisitionTask.cancel(true);
        }
    };

    private final Action startAcquiringAction = new AbstractAction("Start acquiring a single frame", Icon.get("go-last.png")) {
        {
            putValue(Action.SHORT_DESCRIPTION, "Start acquiring a single frame of data from Girino.");
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            synchronized (UI.this) {
                parameters.put(Parameter.THRESHOLD, graphPane.getThreshold());
                parameters.put(Parameter.WAIT_DURATION, graphPane.getWaitDuration());
            }
            currentDataAcquisitionTask = new DataAcquisitionTask(false);
            currentDataAcquisitionTask.execute();
        }
    };

    private final Action startAcquiringInLoopAction = new AbstractAction("Start acquiring in loop", Icon.get("go-next.png")) {
        {
            putValue(Action.SHORT_DESCRIPTION, "Start acquiring data in loop from Girino.");
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            synchronized (UI.this) {
                parameters.put(Parameter.THRESHOLD, graphPane.getThreshold());
                parameters.put(Parameter.WAIT_DURATION, graphPane.getWaitDuration());
            }
            currentDataAcquisitionTask = new DataAcquisitionTask(true);
            currentDataAcquisitionTask.execute();
        }
    };

    private final Action setDisplayedSignalReferentia = new AbstractAction("Change signal interpretation") {

        @Override
        public void actionPerformed(ActionEvent event) {
            Axis.Builder builder = CustomAxisEditionDialog.edit(UI.this, yAxisBuilder);
            if (builder != null) {
                yAxisBuilder = builder;
                yAxisBuilder.save(settings, device.id + ".");
                graphPane.setYCoordinateSystem(yAxisBuilder.build());
            }
        }
    };

    private final Action aboutAction = new AbstractAction("About Girinoscope", Icon.get("help-about.png")) {

        @Override
        public void actionPerformed(ActionEvent event) {
            new AboutDialog(UI.this).setVisible(true);
        }
    };

    private final Action exitAction = new AbstractAction("Quit", Icon.get("application-exit.png")) {

        @Override
        public void actionPerformed(ActionEvent event) {
            dispose();
        }
    };

    public UI() {
        super.setTitle("Girinoscope");

        List<Image> icons = new LinkedList<>();
        for (int i = 256; i >= 16; i /= 2) {
            icons.add(Icon.getImage("icon-" + i + ".png"));
        }
        super.setIconImages(icons);

        super.setLayout(new BorderLayout());

        graphPane = new GraphPane();
        graphPane.setYCoordinateSystem(yAxisBuilder.build());
        graphPane.setPreferredSize(new Dimension(800, 600));
        super.add(graphPane, BorderLayout.CENTER);

        super.setJMenuBar(createMenuBar());

        super.add(createToolBar(), BorderLayout.NORTH);

        statusBar = new StatusBar();
        super.add(statusBar, BorderLayout.SOUTH);

        stopAcquiringAction.setEnabled(false);
        exportLastFrameAction.setEnabled(false);

        if (port != null) {
            startAcquiringAction.setEnabled(true);
            startAcquiringInLoopAction.setEnabled(true);
        } else {
            startAcquiringAction.setEnabled(false);
            startAcquiringInLoopAction.setEnabled(false);
            setStatus("red", "No USB to serial adaptation port detected.");
        }

        super.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                settings.save();
            }
        });
    }

    /*
     * It’s convenient, but not semantically correct, to shutdown the executor
     * and disconnect Girino here.
     */
    @Override
    public void dispose() {
        try {
            if (currentDataAcquisitionTask != null) {
                currentDataAcquisitionTask.cancel(true);
            }
            executor.shutdownNow();
            try {
                executor.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                LOGGER.log(Level.WARNING, "Serial line not responding.", e);
            }
            girino.disconnect();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "When disconnecting from Girino.", e);
        }
        super.dispose();
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("File");
        fileMenu.add(exitAction);
        menuBar.add(fileMenu);

        JMenu girinoMenu = new JMenu("Girino");
        createDynamicDeviceMenu(girinoMenu);
        menuBar.add(girinoMenu);

        JMenu displayMenu = new JMenu("Display");
        displayMenu.add(setDisplayedSignalReferentia);
        displayMenu.add(createDataStrokeWidthMenu());
        menuBar.add(displayMenu);

        JMenu helpMenu = new JMenu("Help");
        helpMenu.add(aboutAction);
        menuBar.add(helpMenu);

        return menuBar;
    }

    private void createDynamicDeviceMenu(final JMenu girinoMenu) {
        Device selectedDevice = null;
        String deviceName = settings.get("device", null);
        for (final Device otherDevice : Device.DEVICES) {
            if (Objects.equals(deviceName, otherDevice.id)) {
                selectedDevice = otherDevice;
                break;
            }
        }

        final JMenu menu = new JMenu("Device");
        ButtonGroup group = new ButtonGroup();
        for (final Device newDevice : Device.DEVICES) {
            Action setDevice = new AbstractAction(newDevice.description) {

                @Override
                public void actionPerformed(ActionEvent event) {
                    synchronized (UI.this) {
                        device = newDevice;
                        parameters = newDevice.getDefaultParameters(new EnumMap<Parameter, Integer>(Parameter.class));
                    }
                    graphPane.setFrameFormat(device.getFrameFormat());
                    graphPane.setThreshold(parameters.get(Parameter.THRESHOLD));
                    graphPane.setWaitDuration(parameters.get(Parameter.WAIT_DURATION));

                    yAxisBuilder.load(settings, device.id + ".");
                    graphPane.setYCoordinateSystem(yAxisBuilder.build());

                    girinoMenu.removeAll();
                    girinoMenu.add(menu);
                    girinoMenu.add(createSerialMenu());
                    if (device.isUserConfigurable(Parameter.PRESCALER)) {
                        girinoMenu.add(createPrescalerMenu(device));
                    }
                    if (device.isUserConfigurable(Parameter.TRIGGER_EVENT)) {
                        girinoMenu.add(createTriggerEventMenu());
                    }
                    if (device.isUserConfigurable(Parameter.VOLTAGE_REFERENCE)) {
                        girinoMenu.add(createVoltageReferenceMenu());
                    }

                    settings.put("device", device.id);
                }
            };
            AbstractButton button = new JCheckBoxMenuItem(setDevice);
            if (selectedDevice == null && device == null || newDevice == selectedDevice) {
                button.doClick();
            }

            group.add(button);

            menu.add(button);
        }
    }

    private JMenu createSerialMenu() {
        JMenu menu = new JMenu("Serial port");
        ButtonGroup group = new ButtonGroup();
        for (final SerialPort newPort : Serial.enumeratePorts()) {
            Action setSerialPort = new AbstractAction(newPort.getSystemPortName()) {

                @Override
                public void actionPerformed(ActionEvent event) {
                    port = newPort;
                }
            };
            AbstractButton button = new JCheckBoxMenuItem(setSerialPort);
            if (port == null) {
                button.doClick();
            }
            group.add(button);
            menu.add(button);
        }
        return menu;
    }

    private JMenu createPrescalerMenu(Device potentialDevice) {
        JMenu menu = new JMenu("Acquisition rate / Time frame");
        ButtonGroup group = new ButtonGroup();
        for (final PrescalerInfo info : potentialDevice.getPrescalerInfoValues()) {
            Action setPrescaler = new AbstractAction(format(info)) {

                @Override
                public void actionPerformed(ActionEvent event) {
                    synchronized (UI.this) {
                        parameters.put(Parameter.PRESCALER, info.value);
                    }
                    String xFormat = info.timeframe > 0.005 ? "#,##0 ms" : "#,##0.0 ms";
                    Axis xAxis = new Axis(0, info.timeframe * 1000, xFormat);
                    graphPane.setXCoordinateSystem(xAxis);
                }
            };
            AbstractButton button = new JCheckBoxMenuItem(setPrescaler);
            if (info.reallyTooFast) {
                button.setForeground(Color.RED.darker());
            } else if (info.tooFast) {
                button.setForeground(Color.ORANGE.darker());
            }
            if (info.value == parameters.get(Parameter.PRESCALER)) {
                button.doClick();
            }
            group.add(button);
            menu.add(button);
        }
        return menu;
    }

    private static String format(PrescalerInfo info) {

        String[] frequencyUnits = {"kHz", "MHz", "GHz"};
        double frequency = info.frequency;
        int frequencyUnitIndex = 0;
        while (frequencyUnitIndex + 1 < frequencyUnits.length && frequency > 1000) {
            ++frequencyUnitIndex;
            frequency /= 1000;
        }

        String[] timeUnits = {"s", "ms", "μs", "ns"};
        double timeframe = info.timeframe;
        int timeUnitIndex = 0;
        while (timeUnitIndex + 1 < timeUnits.length && timeframe < 1) {
            ++timeUnitIndex;
            timeframe *= 1000;
        }

        return String.format("%.1f %s / %.1f %s",
                frequency, frequencyUnits[frequencyUnitIndex],
                timeframe, timeUnits[timeUnitIndex]);
    }

    private JMenu createTriggerEventMenu() {
        JMenu menu = new JMenu("Trigger event mode");
        ButtonGroup group = new ButtonGroup();
        for (final TriggerEventMode mode : TriggerEventMode.values()) {
            Action setPrescaler = new AbstractAction(mode.description) {

                @Override
                public void actionPerformed(ActionEvent event) {
                    synchronized (UI.this) {
                        parameters.put(Parameter.TRIGGER_EVENT, mode.value);
                    }
                }
            };
            AbstractButton button = new JCheckBoxMenuItem(setPrescaler);
            if (mode.value == parameters.get(Parameter.TRIGGER_EVENT)) {
                button.doClick();
            }
            group.add(button);
            menu.add(button);
        }
        return menu;
    }

    private JMenu createVoltageReferenceMenu() {
        JMenu menu = new JMenu("Voltage reference");
        ButtonGroup group = new ButtonGroup();
        for (final VoltageReference reference : VoltageReference.values()) {
            Action setPrescaler = new AbstractAction(reference.description) {

                @Override
                public void actionPerformed(ActionEvent event) {
                    synchronized (UI.this) {
                        parameters.put(Parameter.VOLTAGE_REFERENCE, reference.value);
                    }
                }
            };
            AbstractButton button = new JCheckBoxMenuItem(setPrescaler);
            if (reference.value == parameters.get(Parameter.VOLTAGE_REFERENCE)) {
                button.doClick();
            }
            group.add(button);
            menu.add(button);
        }
        return menu;
    }

    private JMenu createDataStrokeWidthMenu() {
        JMenu menu = new JMenu("Data stroke width");
        ButtonGroup group = new ButtonGroup();
        for (final int width : new int[]{1, 2, 3}) {
            Action setStrokeWidth = new AbstractAction(width + " px") {

                @Override
                public void actionPerformed(ActionEvent event) {
                    graphPane.setDataStrokeWidth(width);
                }
            };
            AbstractButton button = new JCheckBoxMenuItem(setStrokeWidth);
            if (width == 1) {
                button.doClick();
            }
            group.add(button);
            menu.add(button);
        }
        return menu;
    }

    private JComponent createToolBar() {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        final JButton start = toolBar.add(startAcquiringAction);
        final JButton startLooping = toolBar.add(startAcquiringInLoopAction);
        final JButton stop = toolBar.add(stopAcquiringAction);
        final AtomicReference<JButton> lastStart = new AtomicReference<>(startLooping);
        start.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                lastStart.set(start);
            }
        });
        startLooping.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                lastStart.set(start);
            }
        });
        stop.addPropertyChangeListener("enabled", new PropertyChangeListener() {

            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (stop.isEnabled()) {
                    stop.requestFocusInWindow();
                } else {
                    lastStart.get().requestFocusInWindow();
                }
            }
        });
        toolBar.add(exportLastFrameAction);
        return toolBar;
    }

    private void setStatus(String color, String message, Object... arguments) {
        String formattedMessage = String.format(message != null ? message : "", arguments);
        final String htmlMessage = String.format("<html><font color=%s>%s</color></html>", color, formattedMessage);
        if (SwingUtilities.isEventDispatchThread()) {
            statusBar.setText(htmlMessage);
        } else {
            SwingUtilities.invokeLater(new Runnable() {

                @Override
                public void run() {
                    statusBar.setText(htmlMessage);
                }
            });
        }
    }

    private Map<Parameter, Integer> calculateChanges(Map<Parameter, Integer> frozenParameters) {
        Map<Parameter, Integer> changes = new HashMap<>();
        for (Map.Entry<Parameter, Integer> entry : parameters.entrySet()) {
            Parameter parameter = entry.getKey();
            Integer newValue = entry.getValue();
            if (!Objects.equals(newValue, frozenParameters.get(parameter))) {
                changes.put(parameter, newValue);
            }
        }
        for (Map.Entry<Parameter, Integer> entry : frozenParameters.entrySet()) {
            Parameter parameter = entry.getKey();
            if (!parameters.containsKey(parameter)) {
                changes.put(parameter, null);
            }
        }
        return changes;
    }
}
