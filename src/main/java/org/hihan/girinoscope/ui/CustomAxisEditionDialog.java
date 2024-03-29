package org.hihan.girinoscope.ui;

import org.hihan.girinoscope.utils.ui.DialogHelper;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.DecimalFormat;
import java.util.LinkedList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.NumberFormatter;

@SuppressWarnings("serial")
public class CustomAxisEditionDialog extends JDialog {

    public static Axis.Builder edit(JFrame owner, Axis.Builder axisBuilder) {
        CustomAxisEditionDialog editionDialog = new CustomAxisEditionDialog(owner, axisBuilder);
        editionDialog.setVisible(true);
        return editionDialog.axisBuilder;
    }

    private Axis.Builder axisBuilder = new Axis.Builder();

    private JFormattedTextField startValueTextField;

    private JFormattedTextField endValueTextField;

    private JFormattedTextField incrementTextField;

    private JTextField formatTextField;

    private final Action cancelAction = new AbstractAction("Cancel") {
        @Override
        public void actionPerformed(ActionEvent e) {
            axisBuilder = null;
            setVisible(false);
        }
    };

    private final Action applyAction = new AbstractAction("Apply") {
        @Override
        public void actionPerformed(ActionEvent e) {
            axisBuilder.setStartValue(getDoubleValue(startValueTextField));
            axisBuilder.setEndValue(getDoubleValue(endValueTextField));
            axisBuilder.setIncrement(getDoubleValue(incrementTextField));
            axisBuilder.setFormat(formatTextField.getText());
            setVisible(false);
        }
    };

    private CustomAxisEditionDialog(JFrame owner, Axis.Builder axisBuilder) {
        super(owner, true);
        this.axisBuilder = axisBuilder;

        super.setTitle("Y Axis parameters");
        super.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        super.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                CustomAxisEditionDialog.this.axisBuilder = null;
            }
        });

        super.setLayout(new BorderLayout());
        super.setBackground(Color.WHITE);
        super.add(createEditorPane(), BorderLayout.CENTER);
        super.add(createButtonBar(cancelAction, applyAction), BorderLayout.SOUTH);

        super.pack();
        super.setLocationRelativeTo(owner);
        super.setResizable(false);

        DialogHelper.installEscapeCloseOperation(CustomAxisEditionDialog.this);
    }

    private JPanel createEditorPane() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(8, 24, 8, 24));

        List<NumberFormatter> dynamicFormatters = new LinkedList<>();

        GridBagConstraints constraints = new GridBagConstraints();

        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1;
        constraints.weighty = 1;
        constraints.ipadx = 2;
        constraints.ipady = 2;
        constraints.insets = new Insets(1, 4, 1, 4);
        constraints.gridx = 0;
        constraints.gridy = 0;
        panel.add(styleLabel(new JLabel("Start value")), constraints);

        constraints.gridx++;
        startValueTextField = createNumberField(dynamicFormatters);
        startValueTextField.setValue(axisBuilder.getStartValue());
        panel.add(startValueTextField, constraints);

        constraints.gridy++;
        constraints.gridx = 0;
        panel.add(styleLabel(new JLabel("End value")), constraints);

        constraints.gridx++;
        endValueTextField = createNumberField(dynamicFormatters);
        endValueTextField.setValue(axisBuilder.getEndValue());
        panel.add(endValueTextField, constraints);

        constraints.gridy++;
        constraints.gridx = 0;
        panel.add(styleLabel(new JLabel("Increment")), constraints);

        constraints.gridx++;
        incrementTextField = createNumberField(dynamicFormatters);
        incrementTextField.setValue(axisBuilder.getIncrement());
        panel.add(incrementTextField, constraints);

        constraints.gridy++;
        constraints.gridx = 0;
        panel.add(styleLabel(new JLabel("Format")), constraints);

        constraints.gridx++;
        formatTextField = new JTextField(axisBuilder.getFormat());
        panel.add(formatTextField, constraints);

        constraints.gridy++;
        constraints.gridx = 1;
        panel.add(createNoticePane(panel), constraints);

        constraintInputValues(dynamicFormatters);

        return panel;
    }

    private void constraintInputValues(final List<? extends NumberFormatter> dynamicFormatters) {

        startValueTextField.addPropertyChangeListener("editValid", e
                -> startValueTextField.setBackground(startValueTextField.isEditValid() ? null : Color.RED));

        startValueTextField.addPropertyChangeListener("value", e -> {
            double startValue = getDoubleValue(startValueTextField);
            double endValue = getDoubleValue(endValueTextField);
            double increment = getDoubleValue(incrementTextField);
            if (endValue - startValue < increment) {
                endValueTextField.setValue(startValue + increment);
            }
        });

        endValueTextField.addPropertyChangeListener("editValid", e
                -> endValueTextField.setBackground(endValueTextField.isEditValid() ? null : Color.RED));

        endValueTextField.addPropertyChangeListener("value", e -> {
            double startValue = getDoubleValue(startValueTextField);
            double endValue = getDoubleValue(endValueTextField);
            double increment = getDoubleValue(incrementTextField);
            if (endValue - startValue < increment) {
                startValueTextField.setValue(endValue - increment);
            }
        });

        incrementTextField.addPropertyChangeListener("editValid", e
                -> incrementTextField.setBackground(incrementTextField.isEditValid() ? null : Color.RED));

        incrementTextField.addPropertyChangeListener("value", e -> {
            double startValue = getDoubleValue(startValueTextField);
            double endValue = getDoubleValue(endValueTextField);
            double increment = getDoubleValue(incrementTextField);
            double minIncrement = (endValue - startValue) / 100;
            if (increment < minIncrement) {
                incrementTextField.setValue(minIncrement);
            }
        });

        formatTextField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                update();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                update();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                update();
            }

            private void update() {
                try {
                    formatTextField.setBackground(null);
                    applyAction.setEnabled(true);
                    for (NumberFormatter formatter : dynamicFormatters) {
                        formatter.setFormat(new DecimalFormat(formatTextField.getText()));
                    }
                    startValueTextField.setValue(startValueTextField.getValue());
                    endValueTextField.setValue(endValueTextField.getValue());
                    incrementTextField.setValue(incrementTextField.getValue());
                } catch (IllegalArgumentException e) {
                    formatTextField.setBackground(Color.RED);
                    applyAction.setEnabled(false);
                }
            }
        });
    }

    private JFormattedTextField createNumberField(List<NumberFormatter> dynamicFormatters) {
        final NumberFormatter defaultFormatter = new NumberFormatter(new DecimalFormat(axisBuilder.getFormat()));
        final NumberFormatter displayFormatter = new NumberFormatter(new DecimalFormat(axisBuilder.getFormat()));
        final NumberFormatter editFormatter = new NumberFormatter(new DecimalFormat());

        dynamicFormatters.add(defaultFormatter);
        dynamicFormatters.add(displayFormatter);

        JFormattedTextField numberField = new JFormattedTextField( //
                new DefaultFormatterFactory(defaultFormatter, displayFormatter, editFormatter));
        numberField.setColumns(12);
        return numberField;
    }

    private HtmlPane createNoticePane(JPanel panel) {
        String docUrl = "https://docs.oracle.com/javase/8/docs/api/java/text/DecimalFormat.html";

        StringBuilder notice = new StringBuilder();
        notice.append("<html><body bgcolor='");
        notice.append(HtmlPane.toHexCode(panel.getBackground()));
        notice.append("'>");
        notice.append("<a href='").append(docUrl).append("'>");
        notice.append("Pattern format");
        notice.append("</a></body></html>");

        HtmlPane noticePane = new HtmlPane(notice.toString());
        noticePane.setBorder(BorderFactory.createEmptyBorder());
        return noticePane;
    }

    private <T extends JLabel> T styleLabel(T label) {
        label.setHorizontalAlignment(JLabel.RIGHT);
        return label;
    }

    private JPanel createButtonBar(Action... actions) {
        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 4));
        for (Action action : actions) {
            buttonBar.add(new JButton(action));
        }

        JPanel box = new JPanel(new BorderLayout());
        box.add(new JSeparator(JSeparator.HORIZONTAL), BorderLayout.NORTH);
        box.add(buttonBar, BorderLayout.CENTER);
        return box;
    }

    private double getDoubleValue(JFormattedTextField textField) {
        return ((Number) textField.getValue()).doubleValue();
    }
}
