package org.hihan.girinoscope.comm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FrameFormat {

    public final int sampleCount;

    public final int sampleSizeInBit;

    public final boolean bigEndian;

    public final int sampleMaxValue;

    public FrameFormat(int sampleCount, int sampleSizeInBit, boolean bigEndian, int sampleMaxValue) {
        if (sampleSizeInBit < 1 || sampleSizeInBit > 4) {
            throw new IllegalArgumentException("depthInBit: " + sampleSizeInBit);
        }
        if (sampleCount < 1 || sampleCount > Integer.MAX_VALUE / sampleSizeInBit) {
            throw new IllegalArgumentException("width: " + sampleCount);
        }
        if (sampleMaxValue < 0 || sampleMaxValue > Math.pow(256, sampleSizeInBit) - 1 || sampleMaxValue > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("maxValue: " + sampleMaxValue);
        }

        this.sampleCount = sampleCount;
        this.sampleSizeInBit = sampleSizeInBit;
        this.bigEndian = bigEndian;
        this.sampleMaxValue = sampleMaxValue;
    }

    public int[] readValues(byte... data) {
        List<Integer> byteOffsets = new ArrayList<>(sampleSizeInBit);
        for (int i = 0; i < sampleSizeInBit; ++i) {
            byteOffsets.add(i * 8);
        }
        if (!bigEndian) {
            Collections.reverse(byteOffsets);
        }

        int[] values = new int[data.length / sampleSizeInBit];
        for (int i = 0; i < values.length; ++i) {
            int value = 0;
            for (int j = 0; j < sampleSizeInBit; ++j) {
                value += (data[i * sampleSizeInBit + j] & 0xFF) << byteOffsets.get(j);
            }
            values[i] = value;
        }
        return values;
    }
}
