package org.hihan.girinoscope.ui;

import org.hihan.girinoscope.utils.ui.DesktopApi;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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
import javax.swing.text.AttributeSet;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.ImageView;
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
                file.append('\n');
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
                document.setAsynchronousLoadPriority(-1);
                document.setBase(Icon.class.getResource("/org/hihan/girinoscope/ui/"));
                return document;
            }

            @Override
            public ViewFactory getViewFactory() {
                return new HTMLEditorKit.HTMLFactory() {

                    @Override
                    public View create(Element element) {
                        View view = super.create(element);
                        if (view instanceof ImageView) {
                            ((ImageView) view).setLoadsSynchronously(true);
                        }
                        return view;
                    }
                };
            }
        };

        super.setEditorKit(kit);
        setContentType("text/html");
        super.setText(text);
        super.setEditable(false);

        StyleSheet styleSheet = ((HTMLDocument) super.getDocument()).getStyleSheet();
        styleSheet.addRule("body {bgcolor: white; font-family: Sans-Serif;}");

        super.addHyperlinkListener((final HyperlinkEvent event) -> {
            if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                final String href = getHref(event);
                try {
                    if (!DesktopApi.browse(new URI(href))) {
                        LOGGER.log(Level.WARNING, "Can’t open link {0}.", href);
                    }
                } catch (URISyntaxException e) {
                    LOGGER.log(Level.WARNING, "Malformed URL " + href, e);
                }
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D graphics2D = (Graphics2D) g;
        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        super.paintComponent(g);
        g.dispose();
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
