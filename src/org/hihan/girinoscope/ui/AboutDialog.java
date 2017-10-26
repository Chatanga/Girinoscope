package org.hihan.girinoscope.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.io.IOException;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.WindowConstants;

@SuppressWarnings("serial")
public class AboutDialog extends JDialog {

    public AboutDialog(JFrame owner) {
	super(owner, true);
	setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

	setLayout(new BorderLayout());
	setBackground(Color.WHITE);

	try {
	    HtmlPane htmlPane = new HtmlPane(AboutDialog.class.getResource("about.html"));
	    htmlPane.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
	    htmlPane.setOpaque(true);
	    add(htmlPane, BorderLayout.CENTER);
	} catch (IOException e) {
	    throw new RuntimeException(e);
	}

	pack();
	setLocationRelativeTo(owner);
	setResizable(false);

	DialogHelper.installEscapeCloseOperation(this);
    }
}
