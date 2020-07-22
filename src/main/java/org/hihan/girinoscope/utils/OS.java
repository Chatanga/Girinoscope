package org.hihan.girinoscope.utils;

public enum OS {

    Linux("linux"), MacOSX("mac"), Windows("win"), Other;

    String[] names;

    OS(String... names) {
        this.names = names;
    }

    public static OS resolve() {
        String osName = System.getProperty("os.name");
        for (OS os : OS.values()) {
            for (String name : os.names) {
                if (osName.toLowerCase().contains(name)) {
                    return os;
                }
            }
        }
        return Other;
    }
}
