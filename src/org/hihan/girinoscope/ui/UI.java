package org.hihan.girinoscope.ui;

import gnu.io.CommPortIdentifier;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.WindowConstants;

import org.hihan.girinoscope.comm.Girino;
import org.hihan.girinoscope.comm.Girino.Parameter;
import org.hihan.girinoscope.comm.Girino.PrescalerInfo;
import org.hihan.girinoscope.comm.Serial;

@SuppressWarnings("serial")
public class UI extends JFrame {

    private static final Logger logger = Logger.getLogger(UI.class.getName());

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Failed to load the sysem LaF.", e);
                }

                JFrame frame = new UI();
                frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            }
        });
    }

    private Girino girino = new Girino();

    private CommPortIdentifier portId;

    private Map<Parameter, Integer> parameters = new HashMap<Parameter, Integer>();

    private GraphPane graphPane;

    private StatusBar statusBar;

    private JLabel statusBarLabel;

    private DataAcquisitionTask currentDataAcquisitionTask;

    private class DataAcquisitionTask extends SwingWorker<Void, byte[]> {

        private CommPortIdentifier frozenPortId = portId;

        private Map<Parameter, Integer> frozenParameters = new HashMap<Parameter, Integer>(parameters);

        private ExecutorService executor = Executors.newSingleThreadExecutor();

        public DataAcquisitionTask() {
            startAcquiringAction.setEnabled(false);
            stopAcquiringAction.setEnabled(true);
        }

        public void stop() {
            cancel(true);
        }

        @Override
        protected Void doInBackground() throws Exception {
            setStatus("blue", "Contacting Girino on %s...", frozenPortId.getName());

            try {
                executor.submit(new Callable<Void>() {

                    @Override
                    public Void call() throws Exception {
                        girino.etablishConnection(frozenPortId, frozenParameters);
                        return null;
                    }
                }).get(5, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                throw new TimeoutException("No Girino detected on " + frozenPortId.getName());
            }

            setStatus("blue", "Acquiring data from %s...", frozenPortId.getName());
            while (!isCancelled()) {
                byte[] buffer = girino.acquireData();
                if (buffer != null) {
                    publish(buffer);
                } else {
                    break;
                }
            }
            return null;
        }

        @Override
        protected void process(List<byte[]> buffer) {
            graphPane.setData(buffer.get(buffer.size() - 1));
        }

        @Override
        protected void done() {
            startAcquiringAction.setEnabled(true);
            stopAcquiringAction.setEnabled(false);
            try {
                if (!isCancelled()) {
                    get();
                }
                setStatus("blue", "Done acquiring data from %s.", frozenPortId.getName());
            } catch (ExecutionException e) {
                setStatus("red", e.getCause().getMessage());
            } catch (Exception e) {
                setStatus("red", e.getMessage());
            }
        }
    }

    private final Action startAcquiringAction = new AbstractAction("Start acquiring", new ImageIcon(getClass()
            .getResource("images/media-record.png"))) {
        {
            putValue(Action.SHORT_DESCRIPTION, "Start acquiring data from Girino.");
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            parameters.put(Parameter.THRESHOLD, graphPane.getThreshold());
            currentDataAcquisitionTask = new DataAcquisitionTask();
            currentDataAcquisitionTask.execute();
        }
    };

    private final Action stopAcquiringAction = new AbstractAction("Stop acquiring", new ImageIcon(getClass()
            .getResource("images/media-playback-stop.png"))) {
        {
            putValue(Action.SHORT_DESCRIPTION, "Stop acquiring data from Girino.");
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            currentDataAcquisitionTask.stop();
        }
    };

    private final Action aboutAction = new AbstractAction("About Girinoscope", new ImageIcon(getClass().getResource(
            "images/stock_about.png"))) {

        @Override
        public void actionPerformed(ActionEvent event) {
            new AboutDialog(UI.this).setVisible(true);
        }
    };

    private final Action exitAction = new AbstractAction("Quit", new ImageIcon(getClass().getResource(
            "images/application-exit.png"))) {

        @Override
        public void actionPerformed(ActionEvent event) {
            dispose();
        }
    };

    public UI() {
        setTitle("Girinoscope");

        parameters.put(Parameter.PRESCALER, 32);
        parameters.put(Parameter.THRESHOLD, 150);

        setLayout(new BorderLayout());

        graphPane = new GraphPane(parameters.get(Parameter.THRESHOLD));
        graphPane.setPreferredSize(new Dimension(800, 600));
        add(graphPane, BorderLayout.CENTER);

        statusBarLabel = new JLabel();
        statusBar = new StatusBar();
        statusBar.add(statusBarLabel, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        setJMenuBar(createMenuBar());

        add(createToolBar(), BorderLayout.NORTH);

        stopAcquiringAction.setEnabled(false);

        if (portId != null) {
            startAcquiringAction.setEnabled(true);
        } else {
            startAcquiringAction.setEnabled(false);
            setStatus("red", "No USB to serial adaptation port detected.");
        }

        addWindowListener(new WindowAdapter() {

            @Override
            public void windowClosing(WindowEvent event) {
                if (currentDataAcquisitionTask != null) {
                    currentDataAcquisitionTask.stop();
                }
            }
        });
    }

    @Override
    public void dispose() {
        super.dispose();
        try {
            girino.disconnect();
        } catch (IOException e) {
            logger.log(Level.WARNING, "When disconnecting from Girino.", e);
        }
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("File");
        fileMenu.add(exitAction);
        menuBar.add(fileMenu);

        JMenu toolMenu = new JMenu("Tools");
        toolMenu.add(createSerialMenu());
        toolMenu.add(createPrescalerMenu());
        toolMenu.addSeparator();
        toolMenu.add(createThemeMenu());
        menuBar.add(toolMenu);

        JMenu helpMenu = new JMenu("Help");
        helpMenu.add(aboutAction);
        menuBar.add(helpMenu);

        return menuBar;
    }

    private JMenu createSerialMenu() {
        JMenu menu = new JMenu("Serial port");
        ButtonGroup group = new ButtonGroup();
        for (final CommPortIdentifier portId : Serial.enumeratePorts()) {
            Action setSerialPort = new AbstractAction(portId.getName()) {

                @Override
                public void actionPerformed(ActionEvent event) {
                    UI.this.portId = portId;
                }
            };
            AbstractButton button = new JCheckBoxMenuItem(setSerialPort);
            if (UI.this.portId == null) {
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
        for (final PrescalerInfo info : Girino.getPrescalerInfos()) {
            Action setPrescaler = new AbstractAction(info.description) {

                @Override
                public void actionPerformed(ActionEvent event) {
                    parameters.put(Parameter.PRESCALER, info.value);
                    Axis xAxis = new Axis(0, info.timeframe, "%.0f ms", 7);
                    Axis yAxis = new Axis(-2.5, 2.5, "%.2f V", 5);
                    graphPane.setCoordinateSystem(xAxis, yAxis);
                }
            };
            AbstractButton button = new JCheckBoxMenuItem(setPrescaler);
            if (info.value == parameters.get(Parameter.PRESCALER)) {
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
        for (final LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
            Action setLnF = new AbstractAction(info.getName()) {

                @Override
                public void actionPerformed(ActionEvent event) {
                    try {
                        UIManager.setLookAndFeel(info.getClassName());
                        SwingUtilities.updateComponentTreeUI(getRootPane());
                    } catch (Exception e) {
                        setStatus("red", "Failed to load {} LaF.", info.getName());
                    }
                }
            };
            AbstractButton button = new JCheckBoxMenuItem(setLnF);
            group.add(button);
            menu.add(button);
        }
        return menu;
    }

    private JToolBar createToolBar() {
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
        return toolBar;
    }

    private void setStatus(String color, String message, Object... arguments) {
        final String htmlMessage = String.format("<html><font color=%s>%s</color></html>", color, String.format(
                message != null ? message : "", arguments));
        if (SwingUtilities.isEventDispatchThread()) {
            statusBarLabel.setText(htmlMessage);
        } else {
            SwingUtilities.invokeLater(new Runnable() {

                @Override
                public void run() {
                    statusBarLabel.setText(htmlMessage);
                }
            });
        }
    }
}
