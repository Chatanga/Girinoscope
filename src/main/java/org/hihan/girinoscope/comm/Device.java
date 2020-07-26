package org.hihan.girinoscope.comm;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import static org.hihan.girinoscope.comm.Girino.Parameter.*;

/**
 * A read-only description of a device using the Girino firmware or anything
 * else compatible with it (from the {@link Girino} class point of view).
 */
public class Device {

    /**
     * Support level from the firmware perspective.
     */
    public enum SupportLevel {
        /**
         * Not supported at all.
         */
        NONE,
        /**
         * Dump it, but offer no command to change it.
         */
        READ_ONLY,
        /**
         * Some parameters on Girino could only be blindly changed.
         */
        WRITE_ONLY,
        /**
         * Full support.
         */
        READ_WRITE
    }

    public static final Device[] DEVICES = {createClassic(), createStm32f103mm()};

    // Matching firmware: https://github.com/supacyan/girino
    public static Device createClassic() {

        long baseFrequency = 16_000_000;
        FrameFormat frameFormat = new FrameFormat(1280, 1, true, 255);

        Map<Girino.Parameter, SupportLevel> supportLevels = new HashMap<>();
        supportLevels.put(BUFFER_SIZE, SupportLevel.READ_ONLY);
        supportLevels.put(BAUD_RATE, SupportLevel.READ_ONLY);
        supportLevels.put(PRESCALER, SupportLevel.READ_WRITE);
        supportLevels.put(VOLTAGE_REFERENCE, SupportLevel.WRITE_ONLY);
        supportLevels.put(TRIGGER_EVENT, SupportLevel.READ_WRITE);
        supportLevels.put(WAIT_DURATION, SupportLevel.READ_WRITE);
        supportLevels.put(THRESHOLD, SupportLevel.READ_WRITE);

        Map<Girino.Parameter, Integer> parameterValues = new HashMap<>();
        parameterValues.put(BUFFER_SIZE, frameFormat.sampleCount * frameFormat.sampleSizeInBit);
        parameterValues.put(PRESCALER, 32);
        parameterValues.put(VOLTAGE_REFERENCE, Girino.VoltageReference.AVCC.value);
        parameterValues.put(TRIGGER_EVENT, Girino.TriggerEventMode.TOGGLE.value);
        parameterValues.put(WAIT_DURATION, frameFormat.sampleCount - 32);
        parameterValues.put(THRESHOLD, 150);

        List<Girino.PrescalerInfo> infos = new LinkedList<>();
        for (int n = 2; n < 8; ++n) {
            int value = (int) Math.pow(2, n);
            double clockCycleCountPerConversion = 13;
            double frequency = baseFrequency / value / clockCycleCountPerConversion;
            double timeframe = frameFormat.sampleCount / frequency;
            boolean tooFast = n < 5;
            boolean reallyTooFast = n < 3;
            infos.add(new Girino.PrescalerInfo(value, frequency, timeframe, tooFast, reallyTooFast));
        }

        return new Device(
                "classic",
                "Arduino classic",
                2000,
                "Girino ready",
                frameFormat,
                infos,
                supportLevels,
                parameterValues);
    }

    // Matching firmware: https://github.com/ag88/GirinoSTM32F103duino
    public static Device createStm32f103mm() {

        FrameFormat frameFormat = new FrameFormat(1280, 2, true, 4095);

        Map<Girino.Parameter, SupportLevel> supports = new HashMap<>();
        supports.put(BUFFER_SIZE, SupportLevel.READ_ONLY);
        supports.put(BAUD_RATE, SupportLevel.READ_ONLY);
        supports.put(PRESCALER, SupportLevel.READ_WRITE);
        supports.put(VOLTAGE_REFERENCE, SupportLevel.NONE);
        supports.put(TRIGGER_EVENT, SupportLevel.READ_WRITE);
        supports.put(WAIT_DURATION, SupportLevel.READ_WRITE);
        supports.put(THRESHOLD, SupportLevel.READ_WRITE);

        Map<Girino.Parameter, Integer> parameters = new HashMap<>();
        parameters.put(BUFFER_SIZE, frameFormat.sampleCount * frameFormat.sampleSizeInBit);
        parameters.put(PRESCALER, 128);
        parameters.put(TRIGGER_EVENT, Girino.TriggerEventMode.TOGGLE.value);
        parameters.put(WAIT_DURATION, frameFormat.sampleCount - 32);
        parameters.put(THRESHOLD, 150);

        List<Girino.PrescalerInfo> infos = new LinkedList<>();
        double defaultFrequency = 16_000_000d / parameters.get(PRESCALER) / 13d;
        List<Double>frequencies = Arrays.asList(857_000d, 600_000d, 500_000d, 50_000d, 5_000d, 500d, 50d, defaultFrequency);
        Collections.sort(frequencies);
        Collections.reverse(frequencies);
        for (double frequency : frequencies) {
            int pseudoPrescaler = (int) (16_000_000 / frequency / 13);
            double adjustedFrequency = 16_000_000.0 / pseudoPrescaler / 13.0;
            double timeframe = frameFormat.sampleCount / adjustedFrequency;
            infos.add(new Girino.PrescalerInfo(pseudoPrescaler, adjustedFrequency, timeframe, false, false));
        }

        return new Device(
                "stm32f103mm",
                "STM32duino (experimental)",
                2000,
                "Girino stm32f103mm ready",
                frameFormat,
                infos,
                supports,
                parameters);
    }

    public final String id;

    public final String description;

    /**
     * Milliseconds to wait once a new connection has been etablished.
     */
    private final long setupDelayOnReset;

    private final String readyMessage;

    private final FrameFormat frameFormat;

    private final List<Girino.PrescalerInfo> prescalerInfoValues;

    private final Map<Girino.Parameter, SupportLevel> parameterSupportLevels;

    private final Map<Girino.Parameter, Integer> factoryParameterValues;

    private Device(
            String id,
            String description,
            long setupDelayOnReset,
            String readyMessage,
            FrameFormat frameFormat,
            List<Girino.PrescalerInfo> prescalerInfoValues,
            Map<Girino.Parameter, SupportLevel> parameterSupportLevels,
            Map<Girino.Parameter, Integer> factoryParameterValues) {

        if (prescalerInfoValues.isEmpty()) {
            throw new IllegalArgumentException("Need at least 1 prescaler value!");
        }

        this.id = id;
        this.description = description;
        this.setupDelayOnReset = setupDelayOnReset;
        this.readyMessage = readyMessage;
        this.frameFormat = frameFormat;
        this.prescalerInfoValues = prescalerInfoValues;
        this.parameterSupportLevels = parameterSupportLevels;
        this.factoryParameterValues = factoryParameterValues;
    }

    public long getSetupDelayOnReset() {
        return setupDelayOnReset;
    }

    public String getReadyMessage() {
        return readyMessage;
    }

    public FrameFormat getFrameFormat() {
        return frameFormat;
    }

    public List<Girino.PrescalerInfo> getPrescalerInfoValues() {
        return prescalerInfoValues;
    }

    public boolean isUserConfigurable(Girino.Parameter parameter) {
        SupportLevel supportLevel = parameterSupportLevels.get(parameter);
        if (supportLevel != null) {
            switch (supportLevel) {
                case NONE:
                case READ_ONLY:
                    return false;
                case READ_WRITE:
                case WRITE_ONLY:
                    return true;
                default:
                    throw new IllegalArgumentException(supportLevel.name());
            }
        } else {
            return false;
        }
    }

    public boolean isReadable(Girino.Parameter parameter) {
        SupportLevel supportLevel = parameterSupportLevels.get(parameter);
        if (supportLevel != null) {
            switch (supportLevel) {
                case NONE:
                case WRITE_ONLY:
                    return false;
                case READ_WRITE:
                case READ_ONLY:
                    return true;
                default:
                    throw new IllegalArgumentException(supportLevel.name());
            }
        } else {
            return false;
        }
    }

    public boolean isWritable(Girino.Parameter parameter) {
        SupportLevel supportLevel = parameterSupportLevels.get(parameter);
        if (supportLevel != null) {
            switch (supportLevel) {
                case NONE:
                case READ_ONLY:
                    return false;
                case WRITE_ONLY:
                case READ_WRITE:
                    return true;
                default:
                    throw new IllegalArgumentException(supportLevel.name());
            }
        } else {
            return false;
        }
    }

    public Map<Girino.Parameter, Integer> getDefaultParameters(Map<Girino.Parameter, Integer> parameters) {
        factoryParameterValues.forEach((key, value) -> parameters.put(key, value));
        return parameters;
    }
}
