package org.hihan.girinoscope.ui;

import java.awt.Color;
import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JEditorPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.Document;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;

@SuppressWarnings("serial")
public class HtmlPane extends JEditorPane {

    private static final Logger LOGGER = Logger.getLogger(HtmlPane.class.getName());

    public static String toHexCode(Color color) {
        return String.format("#%02X%02X%02X%02X", //
                color.getRed(), //
                color.getGreen(), //
                color.getBlue(), //
                color.getTransparency());
    }

    private static String loadContent(URL url) throws IOException {
        try ( InputStream input = url.openStream()) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
            StringBuilder file = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                file.append(line);
            }
            return file.toString();
        }
    }

    private static String expand(String text, Properties properties) {
        Pattern variablePattern = Pattern.compile("\\$\\{([A-Za-z0-9.\\-_]+)\\}");
        Matcher matcher = variablePattern.matcher(text);
        StringBuffer expandedText = new StringBuffer();
        while (matcher.find()) {
            matcher = matcher.appendReplacement(expandedText, properties.getProperty(matcher.group(1)));
        }
        matcher.appendTail(expandedText);
        return expandedText.toString();
    }

    public HtmlPane(URL url) throws IOException {
        this(loadContent(url));
    }

    public HtmlPane(URL url, Properties properties) throws IOException {
        this(expand(loadContent(url), properties));
    }

    public HtmlPane(String text) {
        HTMLEditorKit kit = new HTMLEditorKit() {
            @Override
            public Document createDefaultDocument() {
                HTMLDocument document = (HTMLDocument) super.createDefaultDocument();
                document.setBase(Icon.class.getResource("/org/hihan/girinoscope/ui/"));
                return document;
            }
        };

        super.setEditorKit(kit);
        setContentType("text/html");
        super.setText(text);
        super.setEditable(false);

        StyleSheet styleSheet = ((HTMLDocument) super.getDocument()).getStyleSheet();
        styleSheet.addRule("body {bgcolor: white; font-family: Sans-Serif;}");

        if (Desktop.isDesktopSupported()) {
            final Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.BROWSE)) {
                super.addHyperlinkListener(new HyperlinkListener() {
                    @Override
                    public void hyperlinkUpdate(final HyperlinkEvent event) {
                        if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                            final String href = getHref(event);
                            try {
                                desktop.browse(new URI(href));
                            } catch (URISyntaxException | IOException e) {
                                LOGGER.log(Level.WARNING, "Can’t open link " + href, e);
                            }
                        }
                    }
                });
            }
        }
    }

    private static String getHref(HyperlinkEvent event) {
        AttributeSet attributes = event.getSourceElement().getAttributes();
        return Objects.requireNonNull(getAttribute((AttributeSet) getAttribute(attributes, "a"), "href")).toString();
    }

    private static Object getAttribute(AttributeSet attributes, String name) {
        for (Enumeration<?> enumeration = attributes.getAttributeNames(); enumeration.hasMoreElements();) {
            Object nameKey = enumeration.nextElement();
            if (name.equals(nameKey.toString())) {
                return attributes.getAttribute(nameKey);
            }
        }
        return null;
    }
}
