package org.hihan.girinoscope.ui;

import com.fazecast.jSerialComm.SerialPort;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.ButtonGroup;
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
import javax.swing.WindowConstants;
import org.hihan.girinoscope.comm.Girino;
import org.hihan.girinoscope.comm.Girino.Parameter;
import org.hihan.girinoscope.comm.Girino.PrescalerInfo;
import org.hihan.girinoscope.comm.Girino.TriggerEventMode;
import org.hihan.girinoscope.comm.Girino.VoltageReference;
import org.hihan.girinoscope.comm.Serial;
import org.pushingpixels.substance.api.SubstanceLookAndFeel;
import org.pushingpixels.substance.api.skin.CeruleanSkin;
import org.pushingpixels.substance.api.skin.SkinInfo;

@SuppressWarnings("serial")
public class UI extends JFrame {

    private static final Logger logger = Logger.getLogger(UI.class.getName());

    public static void main(String[] args) throws Exception {
        Set<String> flags = new HashSet<>(Arrays.asList(args));
        final boolean noLaf = flags.contains("-nolaf");

        Logger rootLogger = Logger.getLogger("org.hihan.girinoscope");
        rootLogger.setLevel(Level.WARNING);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new SimpleFormatter());
        handler.setLevel(Level.ALL);
        rootLogger.addHandler(handler);

        JFrame.setDefaultLookAndFeelDecorated(true);
        JDialog.setDefaultLookAndFeelDecorated(true);

        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                if (!noLaf) {
                    SubstanceLookAndFeel.setSkin(new CeruleanSkin());
                }
                JFrame frame = new UI();
                frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            }
        });
    }

    // The Girino protocol interface.
    private final Girino girino = new Girino();

    // The currently selected serial port used to connect to the Girino hardware.
    private SerialPort port;

    // The edited Girino settings which could be different from the ones uploaded to the Girino hardware.
    private Map<Parameter, Integer> parameters = Girino.getDefaultParameters(new EnumMap<Parameter, Integer>(Parameter.class));

    // Helper class storing the attributes of the Y axis in order to create new instances.
    private Axis.Builder yAxisBuilder = new Axis.Builder();

    private GraphPane graphPane;

    private final StatusBar statusBar;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private DataAcquisitionTask currentDataAcquisitionTask;

    /*
     * All the communication with the Girino interface is done asynchrously through this class (save the disposal).
     */
    private class DataAcquisitionTask extends SwingWorker<Void, byte[]> {

        private SerialPort frozenPort;

        private final Map<Parameter, Integer> frozenParameters = new HashMap<>();

        public DataAcquisitionTask() {
            startAcquiringAction.setEnabled(false);
            stopAcquiringAction.setEnabled(true);
            exportLastFrameAction.setEnabled(true);
        }

        @Override
        protected Void doInBackground() throws Exception {
            while (!isCancelled()) {
                updateConnection();
                acquireData();
            }
            return null;
        }

        private void updateConnection() throws Exception {
            synchronized (UI.this) {
                frozenPort = port;
                frozenParameters.putAll(parameters);
            }

            setStatus("blue", "Contacting Girino on %s...", frozenPort.getSystemPortName());

            Future<Void> connection = executor.submit(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    girino.connect(frozenPort, frozenParameters);
                    return null;
                }
            });
            try {
                connection.get(15, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
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
                    updateConnection = !calculateChanges(frozenParameters).isEmpty() || frozenPort != port;
                }
                if (updateConnection) {
                    if (acquisition != null) {
                        acquisition.cancel(true);
                    }
                    terminated = true;
                } else {
                    try {
                        if (acquisition == null) {
                            acquisition = executor.submit(new Callable<byte[]>() {
                                @Override
                                public byte[] call() throws Exception {
                                    return girino.acquireData();
                                }
                            });
                        }
                        byte[] buffer = acquisition.get(1, TimeUnit.SECONDS);
                        if (buffer != null) {
                            publish(buffer);
                            acquisition = null;
                            terminated = false;
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
        protected void process(List<byte[]> buffer) {
            logger.log(Level.FINE, "{0} data buffer(s) to display.", buffer.size());
            graphPane.setData(buffer.get(buffer.size() - 1));
        }

        @Override
        protected void done() {
            startAcquiringAction.setEnabled(true);
            stopAcquiringAction.setEnabled(false);
            exportLastFrameAction.setEnabled(true);
            try {
                if (!isCancelled()) {
                    get();
                }
                setStatus("blue", "Done acquiring data from %s.", frozenPort.getSystemPortName());
            } catch (ExecutionException e) {
                e.printStackTrace();
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
                byte[] data = graphPane.getData();
                BufferedWriter writer = null;
                try {
                    writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
                    for (int i = 0; i < data.length; ++i) {
                        writer.write(String.format("%d;%d", i, data[i]));
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

    private final Action startAcquiringAction = new AbstractAction("Start acquiring", Icon.get("media-record.png")) {
        {
            putValue(Action.SHORT_DESCRIPTION, "Start acquiring data from Girino.");
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            synchronized (UI.this) {
                parameters.put(Parameter.THRESHOLD, graphPane.getThreshold());
                parameters.put(Parameter.WAIT_DURATION, graphPane.getWaitDuration());
            }
            currentDataAcquisitionTask = new DataAcquisitionTask();
            currentDataAcquisitionTask.execute();
        }
    };

    private final Action setDisplayedSignalReferentia = new AbstractAction("Change signal interpretation") {

        @Override
        public void actionPerformed(ActionEvent event) {
            Axis.Builder builder = CustomAxisEditionDialog.edit(UI.this, yAxisBuilder);
            if (builder != null) {
                yAxisBuilder = builder;
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
        setTitle("Girinoscope");

        List<Image> icons = new LinkedList<>();
        for (int i = 256; i >= 16; i /= 2) {
            icons.add(Icon.getImage("icon-" + i + ".png"));
        }
        setIconImages(icons);

        setLayout(new BorderLayout());

        graphPane = new GraphPane(parameters.get(Parameter.THRESHOLD), parameters.get(Parameter.WAIT_DURATION));
        graphPane.setYCoordinateSystem(yAxisBuilder.build());
        graphPane.setPreferredSize(new Dimension(800, 600));
        add(graphPane, BorderLayout.CENTER);

        setJMenuBar(createMenuBar());

        add(createToolBar(), BorderLayout.NORTH);

        statusBar = new StatusBar();
        add(statusBar, BorderLayout.SOUTH);

        stopAcquiringAction.setEnabled(false);
        exportLastFrameAction.setEnabled(false);

        if (port != null) {
            startAcquiringAction.setEnabled(true);
        } else {
            startAcquiringAction.setEnabled(false);
            setStatus("red", "No USB to serial adaptation port detected.");
        }
    }

    // It’s convenient, but not semantically correct, to shutdown the executor and disconnect Girino here.
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
                logger.log(Level.WARNING, "Serial line not responding.", e);
            }
            girino.disconnect();
        } catch (IOException e) {
            logger.log(Level.WARNING, "When disconnecting from Girino.", e);
        }
        super.dispose();
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("File");
        fileMenu.add(exitAction);
        menuBar.add(fileMenu);

        JMenu girinoMenu = new JMenu("Girino");
        girinoMenu.add(createSerialMenu());
        girinoMenu.add(createPrescalerMenu());
        girinoMenu.add(createTriggerEventMenu());
        girinoMenu.add(createVoltageReferenceMenu());
        menuBar.add(girinoMenu);

        JMenu displayMenu = new JMenu("Display");
        displayMenu.add(setDisplayedSignalReferentia);
        displayMenu.add(createDataStrokeWidthMenu());
        displayMenu.add(createThemeMenu());
        menuBar.add(displayMenu);

        JMenu helpMenu = new JMenu("Help");
        helpMenu.add(aboutAction);
        menuBar.add(helpMenu);

        return menuBar;
    }

    private JMenu createSerialMenu() {
        JMenu menu = new JMenu("Serial port");
        ButtonGroup group = new ButtonGroup();
        for (final SerialPort port : Serial.enumeratePorts()) {
            Action setSerialPort = new AbstractAction(port.getSystemPortName()) {

                @Override
                public void actionPerformed(ActionEvent event) {
                    UI.this.port = port;
                }
            };
            AbstractButton button = new JCheckBoxMenuItem(setSerialPort);
            if (UI.this.port == null) {
                button.doClick();
            }
            group.add(button);
            menu.add(button);
        }
        return menu;
    }

    private JMenu createPrescalerMenu() {
        JMenu menu = new JMenu("Acquisition rate / Time frame");
        ButtonGroup group = new ButtonGroup();
        for (final PrescalerInfo info : PrescalerInfo.values()) {
            Action setPrescaler = new AbstractAction(info.description) {

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

    private JMenu createThemeMenu() {
        JMenu menu = new JMenu("Theme");
        ButtonGroup group = new ButtonGroup();

        for (final Map.Entry<String, SkinInfo> entry : SubstanceLookAndFeel.getAllSkins().entrySet()) {
            Action setLnF = new AbstractAction(entry.getValue().getDisplayName()) {

                @Override
                public void actionPerformed(ActionEvent event) {
                    try {
                        SubstanceLookAndFeel.setSkin(entry.getValue().getClassName());
                    } catch (Exception e) {
                        setStatus("red", "Failed to load skin {}.", entry.getValue().getDisplayName());
                    }
                }
            };
            AbstractButton button = new JCheckBoxMenuItem(setLnF);
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
        final Component start = toolBar.add(startAcquiringAction);
        final Component stop = toolBar.add(stopAcquiringAction);
        start.addPropertyChangeListener("enabled", new PropertyChangeListener() {

            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (!start.isEnabled()) {
                    stop.requestFocusInWindow();
                }
            }
        });
        stop.addPropertyChangeListener("enabled", new PropertyChangeListener() {

            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (!stop.isEnabled()) {
                    start.requestFocusInWindow();
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
