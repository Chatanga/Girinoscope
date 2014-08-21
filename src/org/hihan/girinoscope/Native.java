package org.hihan.girinoscope;

import java.io.File;
import java.lang.reflect.Field;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.UIManager;

public class Native {

    private static final Logger logger = Logger.getLogger(Native.class.getName());

    public enum OS {

        Linux("linux"), MacOSX("mac"), Windows("win");

        String[] names;

        OS(String... names) {
            this.names = names;
        }

        public static OS resolve(String osName) {
            for (OS os : values()) {
                for (String name : os.names) {
                    if (osName.toLowerCase().contains(name)) {
                        return os;
                    }
                }
            }
            return null;
        }
    }

    public static void setLibraryPath() {
        String osName = System.getProperty("os.name");
        OS os = OS.resolve(osName);
        if (os != null) {
            boolean is64bits = "64".equals(System.getProperty("sun.arch.data.model").toLowerCase());

            String nativePath = System.getProperty("girinoscope.native.path");
            if (nativePath == null) {
                nativePath = "native";
            }
            logger.log(Level.INFO, "Native path is '{0}'.", nativePath);

            File libPath = new File(nativePath);
            libPath = new File(libPath, os.name().toLowerCase());
            libPath = new File(libPath, is64bits ? "lib64" : "lib");

            if (libPath.exists()) {
                /*
                 * http://blog.cedarsoft.com/2010/11/setting-java-library-path-
                 * programmatically
                 */
                try {
                    System.setProperty("java.library.path", libPath.getAbsolutePath());
                    Field fieldSysPath = ClassLoader.class.getDeclaredField("sys_paths");
                    fieldSysPath.setAccessible(true);
                    fieldSysPath.set(null, null);
                } catch (Exception e) {
                    throw new RuntimeException("Fail to force the reload of system paths property.", e);
                }
            } else {
                throw new RuntimeException("Unsupported architecture: " + (is64bits ? "64" : "32") + " bits");
            }
        } else {
            throw new RuntimeException("Unsupported operating system: " + osName);
        }
    }

    public static void setBestLookAndFeel() {
        String osName = System.getProperty("os.name");
        OS os = OS.resolve(osName);
        try {
            boolean lafSet = false;
            if (os == OS.MacOSX) {
                String quaquaLaf = "ch.randelshofer.quaqua.QuaquaLookAndFeel";
                boolean quaquaAvailable;
                try {
                    quaquaAvailable = Native.class.getClassLoader().loadClass(quaquaLaf) != null;
                } catch (ClassNotFoundException e) {
                    quaquaAvailable = false;
                }
                if (quaquaAvailable) {
                    /*
                     * set system properties here that affect Quaqua for example
                     * the default layout policy for tabbed panes.
                     */
                    System.setProperty("Quaqua.tabLayoutPolicy", "wrap");
                    try {
                        UIManager.setLookAndFeel(quaquaLaf);
                        lafSet = true;
                    } catch (Exception e) {
                        lafSet = false;
                    }
                }
            }
            if (!lafSet) {
                /*
                 * The GTK+ would probably be the system LaF in Linux, which is
                 * not necessarily a good idea considering how is broken (in our
                 * case: menu without shadows and with unreadable highlighting).
                 */
                if (os == OS.Linux) {
                    UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
                } else {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to load the sysem LaF.", e);
        }
    }
}
