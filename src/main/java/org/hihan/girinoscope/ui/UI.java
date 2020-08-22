package org.hihan.girinoscope.ui;

import com.fazecast.jSerialComm.SerialPort;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import org.hihan.girinoscope.comm.Device;
import org.hihan.girinoscope.comm.Girino;
import org.hihan.girinoscope.comm.Girino.Parameter;
import org.hihan.girinoscope.comm.Girino.PrescalerInfo;
import org.hihan.girinoscope.comm.Girino.TriggerEventMode;
import org.hihan.girinoscope.comm.Girino.VoltageReference;
import org.hihan.girinoscope.comm.Serial;
import org.hihan.girinoscope.utils.Checksum;
import org.hihan.girinoscope.utils.OS;
import org.hihan.girinoscope.utils.Settings;

@SuppressWarnings("serial")
public class UI extends JFrame {

    private static final Logger LOGGER;

    private static final AtomicReference<Level> currentLevel = new AtomicReference<>(Level.OFF);

    static {
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new SimpleFormatter());
        handler.setLevel(Level.ALL);

        Logger rootLogger = Logger.getLogger("org.hihan.girinoscope");
        rootLogger.setLevel(currentLevel.get());
        rootLogger.setUseParentHandlers(false);
        rootLogger.addHandler(handler);

        LOGGER = Logger.getLogger(UI.class.getName());
    }

    public static void main(String[] args) throws Exception {
        if (OS.resolve() == OS.Linux) {
            System.setProperty("sun.java2d.opengl", "true");
        }
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        String lafClassName = new Settings().get("lookAndFeel", UIManager.getSystemLookAndFeelClassName());
        try {
            UIManager.setLookAndFeel(lafClassName);
        } catch (ReflectiveOperationException | UnsupportedLookAndFeelException e) {
            LOGGER.log(Level.WARNING, "When setting the look and feel at startup.", e);
        }

        SwingUtilities.invokeAndWait(() -> {
            JFrame frame = new UI();
            frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    private final Settings settings = new Settings();

    /*
     * The Girino protocol interface.
     */
    private final Girino girino = new Girino();

    private final DeviceModel deviceModel = new DeviceModel();

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

        public final byte[] bytes;

        public ByteArray(byte[] bytes) {
            this.bytes = bytes;
        }
    }

    /*
     * All the communication with the Girino interface is done asynchrously
     * through this class (save the disposal).
     */
    private class DataAcquisitionTask extends SwingWorker<Void, ByteArray> {

        private DeviceModel frozenDeviceModel;

        private final boolean repeated;

        private int frameIndex;

        public DataAcquisitionTask(boolean repeated) {
            this.repeated = repeated;
            startAcquiringAction.setEnabled(false);
            startAcquiringInLoopAction.setEnabled(false);
            stopAcquiringAction.setEnabled(true);
            exportLastFrameAction.setEnabled(false);
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
                frozenDeviceModel = new DeviceModel(deviceModel);
            }

            setStatus("blue", "Contacting Girino on %s...", frozenDeviceModel.getPort().getSystemPortName());

            Future<Void> connection = executor.submit(() -> {
                girino.connect(frozenDeviceModel.getDevice(), frozenDeviceModel.getPort(), frozenDeviceModel.toParameters());
                return null;
            });
            try {
                connection.get(15, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                connection.cancel(true);
                throw new TimeoutException("No Girino detected on " + frozenDeviceModel.getPort().getSystemPortName());
            } catch (InterruptedException e) {
                connection.cancel(true);
                throw e;
            }
        }

        private void acquireData() throws Exception {
            frameIndex = 1;
            Future<byte[]> acquisition = null;
            boolean terminated;
            do {
                setStatus("blue", "Acquiring data frame %d from %s...", frameIndex, frozenDeviceModel.getPort().getSystemPortName());
                boolean updateConnection;
                synchronized (UI.this) {
                    // TODO Explain.
                    frozenDeviceModel.setThreshold(graphPane.getThreshold());
                    frozenDeviceModel.setWaitDuration(graphPane.getWaitDuration());

                    updateConnection = !frozenDeviceModel.equals(deviceModel);
                }
                if (updateConnection) {
                    if (acquisition != null) {
                        acquisition.cancel(true);
                    }
                    terminated = true;
                } else {
                    if (acquisition == null) {
                        acquisition = executor.submit(girino::acquireData);
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
                        ++frameIndex;
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
            exportLastFrameAction.setEnabled(true);
        }

        @Override
        protected void done() {
            startAcquiringAction.setEnabled(true);
            startAcquiringInLoopAction.setEnabled(true);
            stopAcquiringAction.setEnabled(false);
            try {
                if (!isCancelled()) {
                    get();
                }
                setStatus("blue", "Done acquiring data from %s.", frozenDeviceModel.getPort().getSystemPortName());
            } catch (ExecutionException e) {
                LOGGER.log(Level.WARNING, "When acquiring data.", e);
                setStatus("red", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private final Action exportLastFrameAction = makeAction(
            "Export last frame",
            "Export the last time frame to CSV.",
            Icon.get("document-save.png"),
            event -> {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                DateFormat format = new SimpleDateFormat("yyyy_MM_dd-HH_mm");
                fileChooser.setSelectedFile(new File("frame-" + format.format(new Date()) + ".csv"));
                if (fileChooser.showSaveDialog(UI.this) == JFileChooser.APPROVE_OPTION) {
                    File file = fileChooser.getSelectedFile();
                    int[] value = graphPane.getValues();
                    try ( BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
                        for (int i = 0; i < value.length; ++i) {
                            writer.write(Integer.toString(value[i]));
                            writer.newLine();
                        }
                    } catch (IOException e) {
                        setStatus("red", e);
                    }
                }
            });

    private final Action stopAcquiringAction = makeAction(
            "Stop acquiring",
            "Stop acquiring data from Girino.",
            Icon.get("media-playback-stop.png"),
            event -> currentDataAcquisitionTask.cancel(true));

    private final Action startAcquiringAction = makeAction(
            "Start acquiring a single frame",
            "Start acquiring a single frame of data from Girino.",
            Icon.get("go-last.png"),
            event -> {
                synchronized (UI.this) {
                    // TODO Explain.
                    deviceModel.setThreshold(graphPane.getThreshold());
                    deviceModel.setWaitDuration(graphPane.getWaitDuration());
                }
                currentDataAcquisitionTask = new DataAcquisitionTask(false);
                currentDataAcquisitionTask.execute();
            });

    private final Action startAcquiringInLoopAction = makeAction(
            "Start acquiring in loop",
            "Start acquiring data in loop from Girino.",
            Icon.get("go-next.png"),
            event -> {
                synchronized (UI.this) {
                    // TODO Explain.
                    deviceModel.setThreshold(graphPane.getThreshold());
                    deviceModel.setWaitDuration(graphPane.getWaitDuration());
                }
                currentDataAcquisitionTask = new DataAcquisitionTask(true);
                currentDataAcquisitionTask.execute();
            });

    private final Action setDisplayedSignalReferential = makeAction(
            "Change signal interpretation",
            event -> {
                Axis.Builder builder = CustomAxisEditionDialog.edit(UI.this, yAxisBuilder);
                if (builder != null) {
                    yAxisBuilder = builder;
                    yAxisBuilder.save(settings, deviceModel.getDevice().id + ".");
                    graphPane.setYCoordinateSystem(yAxisBuilder.build());
                }
            });

    private final Action aboutAction = makeAction(
            "About Girinoscope",
            event -> new AboutDialog(UI.this).setVisible(true));

    private final Action exitAction = makeAction(
            "Quit",
            event -> dispose());

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
        deviceModel.addPropertyChangeListener(DeviceModel.DEVICE_PROPERTY_NAME, event -> {
            graphPane.setFrameFormat(deviceModel.getDevice().getFrameFormat());
            graphPane.setThreshold(deviceModel.getThreshold());
            graphPane.setWaitDuration(deviceModel.getWaitDuration());

            yAxisBuilder.load(settings, deviceModel.getDevice().id + ".");
            graphPane.setYCoordinateSystem(yAxisBuilder.build());
        });
        super.add(graphPane, BorderLayout.CENTER);

        super.setJMenuBar(createMenuBar());

        super.add(createToolBar(), BorderLayout.NORTH);

        statusBar = new StatusBar();
        super.add(statusBar, BorderLayout.SOUTH);

        stopAcquiringAction.setEnabled(false);
        exportLastFrameAction.setEnabled(false);

        setLastDevice();
        enumeratePorts();

        super.addWindowListener(new WindowAdapter() {

            @Override
            public void windowOpened(WindowEvent arg0) {
                showChangeLogDialogIfNeeded();
            }

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

    private void showChangeLogDialogIfNeeded() {
        try {
            URL url = ChangeLogDialog.class.getResource("CHANGELOG.html");
            String changeLogCheckSum = Checksum.bytesToHex(Checksum.createChecksum(url));
            if (!changeLogCheckSum.equals(settings.get("changeLogCheckSum", null))) {
                settings.put("changeLogCheckSum", changeLogCheckSum);
                new ChangeLogDialog(UI.this).setVisible(true);
            }
        } catch (IOException | NoSuchAlgorithmException e) {
            LOGGER.log(Level.WARNING, "When calculation the change log checksum.", e);
        }
    }

    private void setLastDevice() {
        String deviceName = settings.get("device", null);
        deviceModel.setDevice(Arrays.stream(Device.DEVICES)
                .filter(d -> Objects.equals(deviceName, d.id))
                .findFirst()
                .orElse(Device.DEVICES[0]));
    }

    /*
     * TODO Ensure that old port instances stay relevant after an enumeration.
     */
    private static boolean samePorts(SerialPort leftPort, SerialPort rightPort) {
        if (leftPort == null || rightPort == null) {
            return leftPort == rightPort;
        } else {
            return Objects.equals(leftPort.getSystemPortName(), rightPort.getSystemPortName())
                    && Objects.equals(leftPort.getPortDescription(), rightPort.getPortDescription());
        }
    }

    private List<SerialPort> enumeratePorts() {
        synchronized (UI.this) {
            List<SerialPort> ports = Serial.enumeratePorts();
            SerialPort newPort = ports.stream()
                    .filter(p -> deviceModel.getPort() == null || samePorts(p, deviceModel.getPort()))
                    .findFirst()
                    .orElse(null);

            if (!samePorts(newPort, deviceModel.getPort())) {
                deviceModel.setPort(newPort);
                if (newPort != null) {
                    startAcquiringAction.setEnabled(true);
                    startAcquiringInLoopAction.setEnabled(true);
                    setStatus("blue", "Ready to connect to  %s.", newPort.getSystemPortName());
                } else {
                    startAcquiringAction.setEnabled(false);
                    startAcquiringInLoopAction.setEnabled(false);
                    setStatus("red", "No serial port detected.");
                }
            }

            return ports;
        }
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
        displayMenu.add(setDisplayedSignalReferential);
        displayMenu.add(createDataStrokeWidthMenu());
        displayMenu.add(createThemeMenu());
        menuBar.add(displayMenu);

        JMenu helpMenu = new JMenu("Help");
        helpMenu.add(aboutAction);
        helpMenu.add(createTraceLevelMenu());
        menuBar.add(helpMenu);

        return menuBar;
    }

    private void createDynamicDeviceMenu(final JMenu girinoMenu) {
        final JMenu menu = new JMenu("Device");
        for (final Device newDevice : Device.DEVICES) {
            final JCheckBoxMenuItem menuItem = new JCheckBoxMenuItem(makeAction(newDevice.description, event -> {
                synchronized (UI.this) {
                    deviceModel.setDevice(newDevice);
                    settings.put("device", newDevice.id);
                }
            }));
            menu.add(menuItem);

            deviceModel.addPropertyChangeListener(DeviceModel.DEVICE_PROPERTY_NAME, event -> {
                if (deviceModel.getDevice() == newDevice) {
                    menuItem.setSelected(true);

                    girinoMenu.removeAll();
                    girinoMenu.add(menu);

                    girinoMenu.add(createSerialMenu());

                    if (newDevice.isUserConfigurable(Parameter.PRESCALER)) {
                        Map<PrescalerInfo, Action> prescalerActions = createPrescalerActions(newDevice);
                        girinoMenu.add(createPrescalerMenu(prescalerActions));
                    }

                    if (newDevice.isUserConfigurable(Parameter.TRIGGER_EVENT)) {
                        Map<TriggerEventMode, Action> triggerActions = createTriggerActions();
                        girinoMenu.add(createTriggerEventMenu(triggerActions));
                    }

                    if (newDevice.isUserConfigurable(Parameter.VOLTAGE_REFERENCE)) {
                        girinoMenu.add(createVoltageReferenceMenu());
                    }
                } else {
                    menuItem.setSelected(false);
                }
            });
        }
    }

    private JMenu createSerialMenu() {
        final JMenu menu = new JMenu("Serial port");
        menu.addChangeListener(event -> {
            if (menu.isVisible()) {
                menu.removeAll();
                synchronized (UI.this) {
                    for (final SerialPort newPort : enumeratePorts()) {
                        Action setSerialPort = makeAction(String.format("%s - %s", newPort.getSystemPortName(), newPort.getPortDescription()), e -> {
                            synchronized (UI.this) {
                                deviceModel.setPort(newPort);
                                setStatus("blue", "Ready to connect to %s.", newPort.getSystemPortName());
                            }
                        });
                        AbstractButton button = new JCheckBoxMenuItem(setSerialPort);
                        button.setSelected(samePorts(newPort, newPort));
                        menu.add(button);
                    }
                }
            }
        });
        return menu;
    }

    /*
     * To be called when the {@link device} is set. Will add an observer on
     * {@link prescalerInfo} meant to be deleted when the {@link device} is
     * changed.
     */
    private JMenu createPrescalerMenu(Map<PrescalerInfo, Action> prescalerActions) {
        JMenu menu = new JMenu("Acquisition rate / Time frame");
        for (Map.Entry<PrescalerInfo, Action> prescalerAction : prescalerActions.entrySet()) {
            final PrescalerInfo info = prescalerAction.getKey();
            Action action = prescalerAction.getValue();
            final AbstractButton button = new JCheckBoxMenuItem(action);
            if (info.reallyTooFast) {
                button.setForeground(Color.RED.darker());
            } else if (info.tooFast) {
                button.setForeground(Color.ORANGE.darker());
            }
            deviceModel.addTemporaryPropertyChangeListener(DeviceModel.PRESCALER_INFO_PROPERTY_NAME,
                    event -> button.setSelected(deviceModel.isPrescalerInfoSet(info)));
            button.setSelected(deviceModel.isPrescalerInfoSet(info));
            menu.add(button);
        }
        return menu;
    }

    /*
     * To be called when the {@link device} is set. Will add an observer on
     * {@link triggerEventMode} meant to be deleted when the {@link device} is
     * changed.
     */
    private JMenu createTriggerEventMenu(Map<TriggerEventMode, Action> triggerActions) {
        JMenu menu = new JMenu("Trigger event mode");
        for (Map.Entry<TriggerEventMode, Action> triggerAction : triggerActions.entrySet()) {
            final TriggerEventMode mode = triggerAction.getKey();
            Action action = triggerAction.getValue();
            final AbstractButton button = new JCheckBoxMenuItem(action);
            deviceModel.addTemporaryPropertyChangeListener(DeviceModel.TRIGGER_EVENT_MODE_PROPERTY_NAME,
                    event -> button.setSelected(deviceModel.isTriggerEventModeSet(mode)));
            button.setSelected(deviceModel.isTriggerEventModeSet(mode));
            menu.add(button);
        }
        return menu;
    }

    private JMenu createVoltageReferenceMenu() {
        JMenu menu = new JMenu("Voltage reference");
        ButtonGroup group = new ButtonGroup();
        for (final VoltageReference reference : VoltageReference.values()) {
            Action setPrescaler = makeAction(reference.description, event -> {
                synchronized (UI.this) {
                    deviceModel.setVoltageReference(reference);
                }
            });
            AbstractButton button = new JCheckBoxMenuItem(setPrescaler);
            if (deviceModel.isVoltageReferenceSet(reference)) {
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
            Action setStrokeWidth = makeAction(width + " px", event -> graphPane.setDataStrokeWidth(width));
            AbstractButton button = new JCheckBoxMenuItem(setStrokeWidth);
            if (width == 1) {
                button.doClick();
            }
            group.add(button);
            menu.add(button);
        }
        return menu;
    }

    private JMenu createThemeMenu() {
        String selectedLafClassName = settings.get("lookAndFeel", UIManager.getSystemLookAndFeelClassName());
        JMenu menu = new JMenu("Theme");
        ButtonGroup group = new ButtonGroup();
        for (final LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
            final String lafClassName = info.getClassName();
            Action setLnF = makeAction(info.getName(), event -> {
                try {
                    UIManager.setLookAndFeel(lafClassName);
                    SwingUtilities.updateComponentTreeUI(getRootPane());
                    settings.put("lookAndFeel", lafClassName);
                } catch (ReflectiveOperationException | UnsupportedLookAndFeelException e) {
                    LOGGER.log(Level.WARNING, "When setting the look and feel.", e);
                    setStatus("red", "Failed to set the look and feel.");
                }
            });

            final AbstractButton button = new JCheckBoxMenuItem(setLnF);
            if (Objects.equals(selectedLafClassName, lafClassName)) {
                button.setSelected(true);
            }
            group.add(button);
            menu.add(button);
        }
        return menu;
    }

    private JToolBar createToolBar() {
        final JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        final JButton start = toolBar.add(startAcquiringAction);
        final JButton startLooping = toolBar.add(startAcquiringInLoopAction);
        final JButton stop = toolBar.add(stopAcquiringAction);
        final AtomicReference<JButton> lastStart = new AtomicReference<>(startLooping);
        start.addActionListener(event -> lastStart.set(start));
        startLooping.addActionListener(event -> lastStart.set(start));
        stop.addPropertyChangeListener("enabled", event -> {
            if (stop.isEnabled()) {
                stop.requestFocusInWindow();
            } else {
                lastStart.get().requestFocusInWindow();
            }
        });

        toolBar.add(exportLastFrameAction);

        toolBar.addSeparator();

        final JPanel dynamicToolBarContent = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        dynamicToolBarContent.setOpaque(false);
        toolBar.add(dynamicToolBarContent);

        deviceModel.addPropertyChangeListener(DeviceModel.DEVICE_PROPERTY_NAME, event -> {
            dynamicToolBarContent.removeAll();

            if (deviceModel.getDevice().isUserConfigurable(Parameter.PRESCALER)) {
                Map<PrescalerInfo, Action> prescalerActions = createPrescalerActions(deviceModel.getDevice());
                dynamicToolBarContent.add(new JLabel("Acquisition"));
                dynamicToolBarContent.add(createPrescalerComboBox(prescalerActions));
            }

            if (deviceModel.getDevice().isUserConfigurable(Parameter.TRIGGER_EVENT)) {
                Map<TriggerEventMode, Action> triggerActions = createTriggerActions();
                dynamicToolBarContent.add(new JLabel("Trigger"));
                dynamicToolBarContent.add(createTriggerRadioButtonPane(triggerActions));
            }

            toolBar.revalidate();
        });

        return toolBar;
    }

    /*
     * To be called when the {@link device} is set. Will add an observer on
     * {@link prescalerInfo} meant to be deleted when the {@link device} is
     * changed.
     */
    private JComboBox<PrescalerInfo> createPrescalerComboBox(final Map<PrescalerInfo, Action> prescalerActions) {
        final JComboBox<PrescalerInfo> comboBox = new JComboBox<>();
        comboBox.setFocusable(false);
        comboBox.setToolTipText("Acquisition rate / Time frame");

        comboBox.setRenderer((JList<? extends PrescalerInfo> list, PrescalerInfo info, int index, boolean isSelected, boolean cellHasFocus) -> {
            /*
             * Recreating this renderer is "costly", but it is a simple way to
             * keep it fully up to date with the current LaF. I don’t know if it
             * is a shortcoming of Swing or a(nother) bug in the GTK+ LaF.
             */
            DefaultListCellRenderer defaultRenderer = new DefaultListCellRenderer();

            Component c = defaultRenderer.getListCellRendererComponent(list, format(info), index, isSelected, cellHasFocus);
            if (info.reallyTooFast) {
                defaultRenderer.setForeground(Color.RED.darker());
            } else if (info.tooFast) {
                defaultRenderer.setForeground(Color.ORANGE.darker());
            }
            defaultRenderer.setBorder(new EmptyBorder(7, 4, 7, 4));
            return c;
        });

        prescalerActions.keySet().forEach(comboBox::addItem);

        comboBox.addItemListener(event -> {
            if (event.getStateChange() == ItemEvent.SELECTED) {
                PrescalerInfo info = (PrescalerInfo) event.getItem();
                prescalerActions.get(info).actionPerformed(null);
            }
        });

        deviceModel.addTemporaryPropertyChangeListener(DeviceModel.PRESCALER_INFO_PROPERTY_NAME,
                event -> comboBox.setSelectedItem(deviceModel.getPrescalerInfo()));
        comboBox.setSelectedItem(deviceModel.getPrescalerInfo());

        return comboBox;
    }

    /*
     * To be called when the {@link device} is set. Will add an observer on
     * {@link triggerEventMode} meant to be deleted when the {@link device} is
     * changed.
     */
    private JPanel createTriggerRadioButtonPane(final Map<TriggerEventMode, Action> triggerActions) {
        JPanel panel = new JPanel(new FlowLayout());
        panel.setOpaque(false);
        triggerActions.forEach((mode, action) -> {
            final JToggleButton button = new JToggleButton(action);
            button.setFocusable(false);
            button.setText(null);
            button.setToolTipText(mode.description);

            deviceModel.addTemporaryPropertyChangeListener(DeviceModel.TRIGGER_EVENT_MODE_PROPERTY_NAME,
                    event -> button.setSelected(mode == deviceModel.getTriggerEventMode()));
            button.setSelected(mode == deviceModel.getTriggerEventMode());
            panel.add(button);
        });
        return panel;
    }

    private static String format(PrescalerInfo info) {

        String[] frequencyUnits = {"Hz", "kHz", "MHz", "GHz"};
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

    private Map<PrescalerInfo, Action> createPrescalerActions(Device potentialDevice) {
        Map<PrescalerInfo, Action> prescalerActions = new TreeMap<>((leftInfo, rightInfo) -> Integer.compare(leftInfo.value, rightInfo.value));
        for (final PrescalerInfo info : potentialDevice.getPrescalerInfoValues()) {
            Action setPrescaler = makeAction(format(info), event -> {
                synchronized (UI.this) {
                    deviceModel.setPrescalerInfo(info);
                }
                String xFormat = info.timeframe > 0.005 ? "#,##0 ms" : "#,##0.0 ms";
                Axis xAxis = new Axis(0, info.timeframe * 1000, xFormat);
                graphPane.setXCoordinateSystem(xAxis);
                deviceModel.setPrescalerInfo(info);
            });
            prescalerActions.put(info, setPrescaler);
            if (deviceModel.isPrescalerInfoSet(info)) {
                setPrescaler.actionPerformed(null);
            }
        }
        return prescalerActions;
    }

    private Map<TriggerEventMode, Action> createTriggerActions() {
        Map<TriggerEventMode, Action> triggerActions = new TreeMap<>((leftMode, rightMode) -> Integer.compare(leftMode.value, rightMode.value));
        for (final TriggerEventMode mode : TriggerEventMode.values()) {
            Action setTrigger = makeAction(mode.description, Icon.get(mode.name().toLowerCase() + ".png"), event -> {
                synchronized (UI.this) {
                    deviceModel.setTriggerEventMode(mode);
                }
                deviceModel.setTriggerEventMode(mode);
            });
            triggerActions.put(mode, setTrigger);
            if (deviceModel.isTriggerEventModeSet(mode)) {
                setTrigger.actionPerformed(null);
            }
        }
        return triggerActions;
    }

    private JMenu createTraceLevelMenu() {
        JMenu menu = new JMenu("Trace level");
        ButtonGroup group = new ButtonGroup();
        for (final Level level : new Level[]{Level.ALL, Level.INFO, Level.WARNING, Level.OFF}) {
            Action setLevel = makeAction(level.getName(), event -> {
                Logger rootLogger = Logger.getLogger("org.hihan.girinoscope");
                rootLogger.setLevel(level);
            });

            final AbstractButton button = new JCheckBoxMenuItem(setLevel);
            if (Objects.equals(currentLevel.get(), level)) {
                button.setSelected(true);
            }
            group.add(button);
            menu.add(button);
        }
        return menu;
    }

    private void setStatus(String color, Throwable e) {
        Throwable rootCause = e;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        setStatus(color, rootCause.getMessage());
    }

    private void setStatus(String color, String message, Object... arguments) {
        String formattedMessage = String.format(message != null ? message : "", arguments);
        final String htmlMessage = String.format("<html><font color=%s>%s</color></html>", color, formattedMessage);
        if (SwingUtilities.isEventDispatchThread()) {
            statusBar.setText(htmlMessage);
        } else {
            SwingUtilities.invokeLater(() -> statusBar.setText(htmlMessage));
        }
    }

    private static Action makeAction(String name, Consumer<ActionEvent> behavior) {
        return makeAction(name, null, null, behavior);
    }

    private static Action makeAction(String name, ImageIcon icon, Consumer<ActionEvent> behavior) {
        return makeAction(name, null, icon, behavior);
    }

    private static Action makeAction(String name, String shortDescription, ImageIcon icon, Consumer<ActionEvent> behavior) {
        AbstractAction action = new AbstractAction(name) {

            @Override
            public void actionPerformed(ActionEvent event) {
                behavior.accept(event);
            }
        };

        if (shortDescription != null) {
            action.putValue(Action.SHORT_DESCRIPTION, shortDescription);
        }

        if (icon != null) {
            action.putValue(Action.SMALL_ICON, icon);
        }

        return action;
    }
}
