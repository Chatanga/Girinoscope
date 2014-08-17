package org.hihan.girinoscope.ui.images;

import java.awt.Image;
import java.io.IOException;
import java.net.URL;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

/**
 * For the records, all the icons in this package have been found in my
 * '/usr/icons/Mint-X/actions/16' folders.
 */
public class Icon {

    public static ImageIcon get(String name) {
        URL url = Icon.class.getResource(name);
        if (url != null) {
            return new ImageIcon(url);
        } else {
            throw new IllegalArgumentException("Icon '" + name + "' does not exist.");
        }
    }

    public static Image getImage(String name) {
        URL url = Icon.class.getResource(name);
        if (url != null) {
            try {
                return ImageIO.read(url);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            throw new IllegalArgumentException("Icon '" + name + "' does not exist.");
        }

    }

    private Icon() {
    }
}
