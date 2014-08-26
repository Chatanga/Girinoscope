package org.hihan.girinoscope.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.WindowConstants;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;

@SuppressWarnings("serial")
public class AboutDialog extends JDialog {

    private static final Logger logger = Logger.getLogger(AboutDialog.class.getName());

    public AboutDialog(JFrame owner) {
        super(owner, true);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        setLayout(new BorderLayout());
        setBackground(Color.WHITE);
        add(createEditorPane(), BorderLayout.CENTER);

        pack();
        setLocationRelativeTo(owner);
        setResizable(false);
    }

    private JEditorPane createEditorPane() {
        try {
            InputStream input = getClass().getResource("about.html").openStream();
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(input));
                StringBuilder html = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    html.append(line);
                }
                return createEditorPane(html.toString());
            } finally {
                input.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private JEditorPane createEditorPane(String text) {
        HTMLEditorKit kit = new HTMLEditorKit();
        StyleSheet styleSheet = kit.getStyleSheet();
        styleSheet.addRule("body {bgcolor: white;}");

        JEditorPane editorPane = new JEditorPane();
        editorPane.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        editorPane.setOpaque(true);
        editorPane.setEditorKit(kit);
        editorPane.setContentType("text/html");
        editorPane.setText(text);
        editorPane.setEditable(false);

        editorPane.addHyperlinkListener(new HyperlinkListener() {
            @Override
            public void hyperlinkUpdate(final HyperlinkEvent event) {
                if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                    final String href = getHref(event);
                    try {
                        Desktop.getDesktop().browse(new URI(href));
                    } catch (IOException e) {
                        logger.log(Level.WARNING, "Can’t open link " + href, e);
                    } catch (URISyntaxException e) {
                        logger.log(Level.WARNING, "Can’t open link " + href, e);
                    }
                }
            }
        });

        return editorPane;
    }

    private String getHref(HyperlinkEvent event) {
        AttributeSet attributes = event.getSourceElement().getAttributes();
        return getAttribute((AttributeSet) getAttribute(attributes, "a"), "href").toString();
    }

    private Object getAttribute(AttributeSet attributes, String name) {
        for (Enumeration<?> enumeration = attributes.getAttributeNames(); enumeration.hasMoreElements();) {
            Object nameKey = enumeration.nextElement();
            if (name.equals(nameKey.toString())) {
                return attributes.getAttribute(nameKey);
            }
        }
        return null;
    }
}
