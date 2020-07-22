package org.hihan.girinoscope.utils;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Settings {

    private static final Logger LOGGER = Logger.getLogger(Settings.class.getName());

    private final Properties properties = new Properties();

    private final Path path;

    public Settings() {
        switch (OS.resolve()) {
            case Linux:
                path = getLinuxConfigDir();
                break;
            case MacOSX:
                path = getMacOSXConfigDir();
                break;
            case Windows:
                path = getWindowsConfigDir();
                break;
            default:
                path = getDefaultConfigDir();
                break;
        }

        if (Files.exists(path) && Files.isRegularFile(path) && Files.isReadable(path)) {
            try (InputStream input = new FileInputStream(path.toFile())) {
                properties.clear();
                properties.load(input);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to read saved settings.", e);
            }
        }
    }

    private Path getLinuxConfigDir() {
        String configHome = System.getenv("XDG_CONFIG_HOME");
        if (configHome != null) {
            return Paths.get(configHome, "Girinoscope", "settings.properties");
        } else {
            return Paths.get(System.getenv("HOME"), ".config", "Girinoscope", "settings.properties");
        }
    }

    private Path getMacOSXConfigDir() {
        return Paths.get(System.getenv("HOME"), "Library", "Application Support", "Girinoscope", "settings.properties");
    }

    private Path getWindowsConfigDir() {
        return Paths.get(System.getenv("APPDATA"), "Girinoscope", "settings.properties");
    }

    private Path getDefaultConfigDir() {
        return Paths.get(System.getProperty("user.home"), "Girinoscope", "settings.properties");
    }

    public void save() {
        try {
            Path parentDir = path.getParent();
            if (!Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            try (OutputStream output = new FileOutputStream(path.toFile())) {
                properties.store(output, "Girinoscope settings");
                LOGGER.log(Level.INFO, "Settings saved in {0}.", path);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to save settings.", e);
        }
    }

    public void put(String name, String value) {
        properties.put(name, value);
    }

    public void put(String name, int value) {
        properties.put(name, Integer.toString(value));
    }

    public void put(String name, double value) {
        properties.put(name, Double.toString(value));
    }

    public String get(String name, String defaultValue) {
        String value = (String) properties.get(name);
        return value != null ? value : defaultValue;
    }

    public int get(String name, int defaultValue) {
        String textValue = (String) properties.get(name);
        if (textValue != null) {
            try {
                return Integer.parseInt(textValue);
            } catch (NumberFormatException e) {
                LOGGER.log(Level.WARNING, "Badly formatted integer value '" + name + "': " + textValue, e);
                return defaultValue;
            }
        } else {
            return defaultValue;
        }
    }

    public double get(String name, double defaultValue) {
        String textValue = (String) properties.get(name);
        if (textValue != null) {
            try {
                return Double.parseDouble(textValue);
            } catch (NumberFormatException e) {
                LOGGER.log(Level.WARNING, "Badly formatted double value '" + name + "': " + textValue, e);
                return defaultValue;
            }
        } else {
            return defaultValue;
        }
    }
}
