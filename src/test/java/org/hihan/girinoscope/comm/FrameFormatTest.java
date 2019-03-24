package org.hihan.girinoscope.comm;

import org.junit.Assert;
import org.junit.Test;

public class FrameFormatTest {

    @Test
    public void testReadValues8x1() {
        // Only sampleSizeInBit and bigEndian matter here.
        FrameFormat format = new FrameFormat(1000, 1, true, Byte.MAX_VALUE);
        int[] expected = {0, 30, 200, 255};
        int[] values = format.readValues((byte) 0, (byte) 30, (byte) 200, (byte) 255);
        Assert.assertArrayEquals(expected, values);
    }

    @Test
    public void testReadValuesBigEndian8x2() {
        // Only sampleSizeInBit and bigEndian matter here.
        FrameFormat format = new FrameFormat(1000, 2, true, Short.MAX_VALUE);
        int[] expected = {0, 30, 20000, 32767, 65535};
        int[] values = format.readValues(
                (byte) 0, (byte) 0,
                (byte) 30, (byte) 0,
                (byte) 32, (byte) 78,
                (byte) 255, (byte) 127,
                (byte) 255, (byte) 255);
        Assert.assertArrayEquals(expected, values);
    }

    @Test
    public void testReadValuesLittleEndian8x2() {
        // Only sampleSizeInBit and bigEndian matter here.
        FrameFormat format = new FrameFormat(1000, 2, false, Short.MAX_VALUE);
        int[] expected = {0, 30, 20000, 32767, 65535};
        int[] values = format.readValues(
                (byte) 0, (byte) 0,
                (byte) 0, (byte) 30,
                (byte) 78, (byte) 32,
                (byte) 127, (byte) 255,
                (byte) 255, (byte) 255);
        Assert.assertArrayEquals(expected, values);
    }
}
