package org.hihan.girinoscope.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.io.IOException;
import java.util.Properties;
import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.WindowConstants;

@SuppressWarnings("serial")
public class AboutDialog extends JDialog {

    public AboutDialog(JFrame owner) {
        super(owner, true);
        super.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        super.setLayout(new BorderLayout());
        super.setBackground(Color.WHITE);

        try {
            Properties properties = new Properties();
            properties.setProperty("java.version", System.getProperty("java.version"));
            HtmlPane htmlPane = new HtmlPane(AboutDialog.class.getResource("about.html"), properties);
            htmlPane.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            htmlPane.setOpaque(true);
            super.add(htmlPane, BorderLayout.CENTER);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        super.pack();
        super.setLocationRelativeTo(owner);
        super.setResizable(false);

        DialogHelper.installEscapeCloseOperation(AboutDialog.this);
    }
}
