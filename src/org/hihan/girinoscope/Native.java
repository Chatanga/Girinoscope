package org.hihan.girinoscope;

import java.io.File;
import java.lang.reflect.Field;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

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
            boolean is64bits = "64".equals(System.getProperty("sun.arch.data.model"));

            String nativePath = System.getProperty("girinoscope.native.path");
            if (nativePath == null) {
                nativePath = "native";
            }
            logger.log(Level.INFO, "Native path is {0}.", nativePath);

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

        boolean lafSet = false;
        try {
            /*
             * Could work on other platform actually, but not intended to.
             */
            if (os == OS.MacOSX) {
                /*
                 * set system properties here that affect Quaqua for example the
                 * default layout policy for tabbed panes.
                 */
                System.setProperty("Quaqua.tabLayoutPolicy", "wrap");
                lafSet = setLookAndFeelIfAvailable("ch.randelshofer.quaqua.QuaquaLookAndFeel");
            }
            /*
             * The GTK+ would probably be the system LaF in Linux, which is not
             * necessarily a good idea considering how is broken (in our case:
             * menu without shadows and with unreadable highlighting).
             */
            else if (os == OS.Linux) {
                lafSet = setLookAndFeelIfAvailable("javax.swing.plaf.nimbus.NimbusLookAndFeel");
                if (!lafSet) {
                    lafSet = setLookAndFeelIfAvailable("com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel");
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to load the best LaF.", e);
        }

        if (!lafSet) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to load the system LaF.", e);
            }
        }
    }

    private static boolean setLookAndFeelIfAvailable(String className) throws InstantiationException,
            IllegalAccessException, UnsupportedLookAndFeelException {
        try {
            if (Native.class.getClassLoader().loadClass(className) != null) {
                UIManager.setLookAndFeel(className);
                return true;
            } else {
                return false;
            }
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
