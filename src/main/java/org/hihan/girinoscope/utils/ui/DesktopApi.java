package org.hihan.girinoscope.utils.ui;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.hihan.girinoscope.utils.OS;

/**
 * @see
 * https://stackoverflow.com/questions/18004150/desktop-api-is-not-supported-on-the-current-platform
 */
public class DesktopApi {

    private static final Logger LOGGER = Logger.getLogger(DesktopApi.class.getName());

    public static boolean browse(URI uri) {
        return systemSpecificBrowse(uri.toString()) || desktopBrowse(uri);
    }

    private static boolean systemSpecificBrowse(String what) {
        switch (OS.resolve()) {
            case Linux:
                return startCommand("xdg-open", what) || startCommand("gnome-open", what) || startCommand("kde-open", what);
            case MacOSX:
                return startCommand("open", what);
            case Windows:
                return startCommand("explorer", what);
            case Other:
                return false;
        }
        return false;
    }

    private static boolean desktopBrowse(URI uri) {
        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.BROWSE)) {
                try {
                    LOGGER.log(Level.FINE, "Using Desktop.getDesktop().browse({0}).", uri);
                    desktop.browse(uri);
                    return true;
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "When using desktop browse.", e);
                }
            } else {
                LOGGER.log(Level.FINE, "Desktop browse action is not supported.");
            }
        } else {
            LOGGER.log(Level.FINE, "Desktop is not supported.");
        }
        return false;
    }

    private static class StreamGobbler extends Thread {

        private final String command;

        private final InputStream input;

        StreamGobbler(String command, InputStream input) {
            this.command = command;
            this.input = input;
        }

        public void run() {
            try ( BufferedReader br = new BufferedReader(new InputStreamReader(input))) {
                while (br.readLine() != null) {
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "When consuming output of " + command, e);
            }
        }
    }

    private static boolean startCommand(String... cmdArray) {
        String command = Arrays.toString(cmdArray);
        try {
            Process p = Runtime.getRuntime().exec(cmdArray);

            new StreamGobbler(command, p.getErrorStream()).start();
            new StreamGobbler(command, p.getInputStream()).start();

            LOGGER.log(Level.FINE, "Successfully executed command {0}.", command);
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Failed to execute command {0} ({1}).", new Object[]{command, e.getMessage()});
            return false;
        }
    }

    private DesktopApi() {
    }
}
