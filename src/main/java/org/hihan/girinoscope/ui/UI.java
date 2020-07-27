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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Observable;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
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

    private static final Logger LOGGER = Logger.getLogger(UI.class.getName());

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

    private static class ObservableValue<T> extends Observable {

        private T value;

        public ObservableValue() {
            this(null);
        }

        public ObservableValue(T value) {
            this.value = value;
        }

        public T get() {
            return value;
        }

        public void set(T value) {
            this.value = value;
            setChanged();
            notifyObservers(value);
        }
    }

    private final Settings settings = new Settings();

    /*
     * The Girino protocol interface.
     */
    private final Girino girino = new Girino();

    /*
     * The selected device on which the Girino firmware is running (could be
     * different from the one currently configured for the Girino). Its value
     * should only be changed using {@link #setDevice}.
     */
    private final ObservableValue<Device> device = new ObservableValue<>();

    /*
     * The currently selected serial port used to connect to the Girino
     * hardware.
     */
    private SerialPort port;

    /*
     * A device specific set of values. Any observer on it will be deleted on a
     * device change.
     */
    private final ObservableValue<PrescalerInfo> prescalerInfo = new ObservableValue<>();

    /*
     * A device specific set of values. Any observer on it will be deleted on a
     * device change.
     */
    private final ObservableValue<TriggerEventMode> triggerEventMode = new ObservableValue<>();

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

        private Device frozenDevice;

        private SerialPort frozenPort;

        private final Map<Parameter, Integer> frozenParameters = new HashMap<>();

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
                frozenDevice = device.get();
                frozenPort = port;
                frozenParameters.putAll(parameters);
            }

            setStatus("blue", "Contacting Girino on %s...", frozenPort.getSystemPortName());

            Future<Void> connection = executor.submit(() -> {
                girino.connect(frozenDevice, frozenPort, frozenParameters);
                return null;
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
            frameIndex = 1;
            Future<byte[]> acquisition = null;
            boolean terminated;
            do {
                setStatus("blue", "Acquiring data frame %d from %s...", frameIndex, frozenPort.getSystemPortName());
                boolean updateConnection;
                synchronized (UI.this) {
                    parameters.put(Parameter.THRESHOLD, graphPane.getThreshold());
                    parameters.put(Parameter.WAIT_DURATION, graphPane.getWaitDuration());
                    updateConnection
                            = frozenDevice != device.get()
                            || !samePorts(frozenPort, port)
                            || !calculateChanges(frozenParameters).isEmpty();
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
                setStatus("blue", "Done acquiring data from %s.", frozenPort.getSystemPortName());
            } catch (ExecutionException e) {
                LOGGER.log(Level.WARNING, "When acquiring data.", e);
                setStatus("red", e.getCause().getMessage());
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
                        setStatus("red", e.getMessage());
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
                    parameters.put(Parameter.THRESHOLD, graphPane.getThreshold());
                    parameters.put(Parameter.WAIT_DURATION, graphPane.getWaitDuration());
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
                    parameters.put(Parameter.THRESHOLD, graphPane.getThreshold());
                    parameters.put(Parameter.WAIT_DURATION, graphPane.getWaitDuration());
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
                    yAxisBuilder.save(settings, device.get().id + ".");
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
        device.addObserver((o, v) -> {
            graphPane.setFrameFormat(device.get().getFrameFormat());
            graphPane.setThreshold(parameters.get(Parameter.THRESHOLD));
            graphPane.setWaitDuration(parameters.get(Parameter.WAIT_DURATION));

            yAxisBuilder.load(settings, device.get().id + ".");
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
        setDevice(Arrays.stream(Device.DEVICES)
                .filter(d -> Objects.equals(deviceName, d.id))
                .findFirst()
                .orElse(Device.DEVICES[0]));
    }

    private void setDevice(Device newDevice) {
        prescalerInfo.deleteObservers();
        triggerEventMode.deleteObservers();
        synchronized (UI.this) {
            parameters = newDevice.getDefaultParameters(new EnumMap<>(Parameter.class));
            // Device are read-only and only the instance change need to be guarded.
            device.set(newDevice);
        }
        settings.put("device", newDevice.id);
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
        List<SerialPort> ports = Serial.enumeratePorts();
        SerialPort newPort = ports.stream()
                .filter(p -> port == null || samePorts(p, port))
                .findFirst()
                .orElse(null);

        if (!samePorts(newPort, port)) {
            port = newPort;
            if (port != null) {
                startAcquiringAction.setEnabled(true);
                startAcquiringInLoopAction.setEnabled(true);
                setStatus("blue", "Connected to %s.", port.getSystemPortName());
            } else {
                startAcquiringAction.setEnabled(false);
                startAcquiringInLoopAction.setEnabled(false);
                setStatus("red", "No serial port detected.");
            }
        }

        System.out.println("port = " + port);

        return ports;
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
        menuBar.add(helpMenu);

        return menuBar;
    }

    private void createDynamicDeviceMenu(final JMenu girinoMenu) {
        final JMenu menu = new JMenu("Device");
        for (final Device newDevice : Device.DEVICES) {
            final JCheckBoxMenuItem menuItem = new JCheckBoxMenuItem(makeAction(newDevice.description, event -> setDevice(newDevice)));
            menu.add(menuItem);

            device.addObserver((o, v) -> {
                if (device.get() == newDevice) {
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
                                port = newPort;
                            }
                        });
                        AbstractButton button = new JCheckBoxMenuItem(setSerialPort);
                        button.setSelected(samePorts(port, newPort));
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
            prescalerInfo.addObserver((o, value) -> button.setSelected(info == value));
            button.setSelected(info == prescalerInfo.get());
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
            triggerEventMode.addObserver((Observable o, Object value) -> button.setSelected(mode == value));
            button.setSelected(mode == triggerEventMode.get());
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
                    parameters.put(Parameter.VOLTAGE_REFERENCE, reference.value);
                }
            });
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

        device.addObserver((o, v) -> {
            dynamicToolBarContent.removeAll();

            if (device.get().isUserConfigurable(Parameter.PRESCALER)) {
                Map<PrescalerInfo, Action> prescalerActions = createPrescalerActions(device.get());
                dynamicToolBarContent.add(new JLabel("Acquisition"));
                dynamicToolBarContent.add(createPrescalerComboBox(prescalerActions));
            }
            if (device.get().isUserConfigurable(Parameter.TRIGGER_EVENT)) {
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

        prescalerInfo.addObserver((o, value) -> comboBox.setSelectedItem((PrescalerInfo) value));
        comboBox.setSelectedItem(prescalerInfo.get());

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
            triggerEventMode.addObserver((o, value) -> button.setSelected(mode == (TriggerEventMode) value));
            button.setSelected(mode == triggerEventMode.get());
            panel.add(button);
        });
        return panel;
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

    private Map<PrescalerInfo, Action> createPrescalerActions(Device potentialDevice) {
        Map<PrescalerInfo, Action> prescalerActions = new TreeMap<>((leftInfo, rightInfo) -> Integer.compare(leftInfo.value, rightInfo.value));
        for (final PrescalerInfo info : potentialDevice.getPrescalerInfoValues()) {
            Action setPrescaler = makeAction(format(info), event -> {
                synchronized (UI.this) {
                    parameters.put(Parameter.PRESCALER, info.value);
                }
                String xFormat = info.timeframe > 0.005 ? "#,##0 ms" : "#,##0.0 ms";
                Axis xAxis = new Axis(0, info.timeframe * 1000, xFormat);
                graphPane.setXCoordinateSystem(xAxis);
                prescalerInfo.set(info);
            });
            prescalerActions.put(info, setPrescaler);
            if (info.value == parameters.get(Parameter.PRESCALER)) {
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
                    parameters.put(Parameter.TRIGGER_EVENT, mode.value);
                }
                triggerEventMode.set(mode);
            });
            triggerActions.put(mode, setTrigger);
            if (mode.value == parameters.get(Parameter.TRIGGER_EVENT)) {
                setTrigger.actionPerformed(null);
            }
        }
        return triggerActions;
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

    private Map<Parameter, Integer> calculateChanges(Map<Parameter, Integer> frozenParameters) {
        Map<Parameter, Integer> changes = new HashMap<>();
        parameters.forEach((parameter, newValue) -> {
            if (!Objects.equals(newValue, frozenParameters.get(parameter))) {
                changes.put(parameter, newValue);
            }
        });
        for (Parameter parameter : frozenParameters.keySet()) {
            if (!parameters.containsKey(parameter)) {
                changes.put(parameter, null);
            }
        }
        return changes;
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
