package org.hihan.girinoscope.utils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Checksum {

    public static byte[] createChecksum(URL url) throws IOException, NoSuchAlgorithmException {
        try ( InputStream input = url.openStream()) {
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[1024];
            int sizeRead;
            while ((sizeRead = input.read(buffer)) != -1) {
                messageDigest.update(buffer, 0, sizeRead);
            }
            return messageDigest.digest();
        }
    }

    /**
     * @see
     * https://stackoverflow.com/questions/9655181/how-to-convert-a-byte-array-to-a-hex-string-in-java
     */
    public static String bytesToHex(byte[] bytes) {
        char[] hexArray = "0123456789ABCDEF".toCharArray();
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    private Checksum() {
    }
}
