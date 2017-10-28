package org.hihan.girinoscope.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
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

	setTitle("Y Axis parameters");
	setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

	addWindowListener(new WindowAdapter() {
	    @Override
	    public void windowClosing(WindowEvent e) {
		CustomAxisEditionDialog.this.axisBuilder = null;
	    }
	});

	setLayout(new BorderLayout());
	setBackground(Color.WHITE);
	add(createEditorPane(), BorderLayout.CENTER);
	add(createButtonBar(cancelAction, applyAction), BorderLayout.SOUTH);

	pack();
	setLocationRelativeTo(owner);
	setResizable(false);

	DialogHelper.installEscapeCloseOperation(this);
    }

    private JPanel createEditorPane() {
	JPanel panel = new JPanel(new GridBagLayout());
	panel.setBorder(new EmptyBorder(8, 24, 8, 24));

	List<NumberFormatter> dynamicFormatters = new LinkedList<NumberFormatter>();

	GridBagConstraints constraints = new GridBagConstraints();

	constraints.fill = GridBagConstraints.BOTH;
	constraints.ipadx = 2;
	constraints.ipady = 2;
	constraints.insets = new Insets(1, 4, 1, 4);
	constraints.gridx = 0;
	constraints.gridy = 0;
	panel.add(styleLabel(new JLabel("Start value")), constraints);

	constraints.gridx++;
	startValueTextField = createMumberField(dynamicFormatters);
	startValueTextField.setValue(axisBuilder.getStartValue());
	panel.add(startValueTextField, constraints);

	constraints.gridy++;
	constraints.gridx = 0;
	panel.add(styleLabel(new JLabel("End value")), constraints);

	constraints.gridx++;
	endValueTextField = createMumberField(dynamicFormatters);
	endValueTextField.setValue(axisBuilder.getEndValue());
	panel.add(endValueTextField, constraints);

	constraints.gridy++;
	constraints.gridx = 0;
	panel.add(styleLabel(new JLabel("Increment")), constraints);

	constraints.gridx++;
	incrementTextField = createMumberField(dynamicFormatters);
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

	startValueTextField.addPropertyChangeListener("editValid", new PropertyChangeListener() {
	    @Override
	    public void propertyChange(PropertyChangeEvent evt) {
		startValueTextField.setBackground(startValueTextField.isEditValid() ? null : Color.RED);
	    }
	});

	startValueTextField.addPropertyChangeListener("value", new PropertyChangeListener() {
	    @Override
	    public void propertyChange(PropertyChangeEvent evt) {
		double startValue = getDoubleValue(startValueTextField);
		double endValue = getDoubleValue(endValueTextField);
		double increment = getDoubleValue(incrementTextField);
		if (endValue - startValue < increment) {
		    endValueTextField.setValue(startValue + increment);
		}
	    }
	});

	endValueTextField.addPropertyChangeListener("editValid", new PropertyChangeListener() {
	    @Override
	    public void propertyChange(PropertyChangeEvent evt) {
		endValueTextField.setBackground(endValueTextField.isEditValid() ? null : Color.RED);
	    }
	});

	endValueTextField.addPropertyChangeListener("value", new PropertyChangeListener() {
	    @Override
	    public void propertyChange(PropertyChangeEvent evt) {
		double startValue = getDoubleValue(startValueTextField);
		double endValue = getDoubleValue(endValueTextField);
		double increment = getDoubleValue(incrementTextField);
		if (endValue - startValue < increment) {
		    startValueTextField.setValue(endValue - increment);
		}
	    }
	});

	incrementTextField.addPropertyChangeListener("value", new PropertyChangeListener() {
	    @Override
	    public void propertyChange(PropertyChangeEvent evt) {
		double increment = getDoubleValue(incrementTextField);
		if (increment < 0) {
		    incrementTextField.setValue(-increment);
		}
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

    private JFormattedTextField createMumberField(List<NumberFormatter> dynamicFormatters) {
	final NumberFormatter defaultFormatter = new NumberFormatter(new DecimalFormat(axisBuilder.getFormat()));
	final NumberFormatter displayFormatter = new NumberFormatter(new DecimalFormat(axisBuilder.getFormat()));
	final NumberFormatter editFormatter = new NumberFormatter(new DecimalFormat());

	dynamicFormatters.add(defaultFormatter);
	dynamicFormatters.add(displayFormatter);

	JFormattedTextField numberField = new JFormattedTextField( //
	        new DefaultFormatterFactory(defaultFormatter, displayFormatter, editFormatter));
	numberField.setColumns(8);
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
