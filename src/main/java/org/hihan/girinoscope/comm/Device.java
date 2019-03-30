package org.hihan.girinoscope.comm;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import static org.hihan.girinoscope.comm.Girino.Parameter.*;

// TODO Add a properties file loading capability.
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
            String description = String.format("%.1f kHz / %.1f ms", frequency / 1000, timeframe * 1000);
            infos.add(new Girino.PrescalerInfo(value, frequency, timeframe, description, tooFast, reallyTooFast));
        }

        return new Device(
                "classic",
                "Arduino classic",
                baseFrequency,
                2000,
                "Girino ready",
                frameFormat,
                infos,
                supportLevels,
                parameterValues);
    }

    // Matching firmware: https://github.com/ag88/GirinoSTM32F103duino
    public static Device createStm32f103mm() {

        long baseFrequency = 72_000_000;
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
        parameters.put(PRESCALER, 32);
        //parameters.put(VOLTAGE_REFERENCE, 0);
        parameters.put(TRIGGER_EVENT, Girino.TriggerEventMode.TOGGLE.value);
        parameters.put(WAIT_DURATION, frameFormat.sampleCount - 32);
        parameters.put(THRESHOLD, 150);

        List<Girino.PrescalerInfo> infos = new LinkedList<>();
        for (int n = 2; n < 8; ++n) {
            int value = (int) Math.pow(2, n);
            double clockCycleCountPerConversion = 13; // TODO To be calibrated.
            double frequency = baseFrequency / value / clockCycleCountPerConversion;
            double timeframe = frameFormat.sampleCount / frequency;
            String description = String.format("%.1f kHz / %.1f ms", frequency / 1000, timeframe * 1000);
            infos.add(new Girino.PrescalerInfo(value, frequency, timeframe, description, false, false));
        }

        return new Device(
                "stm32f103mm",
                "STM32duino (experimental)",
                baseFrequency,
                2000,
                "Girino stm32f103mm ready", // TODO Add a version?
                frameFormat,
                infos,
                supports,
                parameters);
    }

    public final String id;

    public final String description;

    /**
     * The Arduino clock frequency in milliseconds.
     */
    public final long baseFrequency;

    /**
     * Milliseconds to wait once a new connection has been etablished.
     */
    public final long setupDelayOnReset;

    public final String readyMessage;

    public final FrameFormat frameFormat;

    public final List<Girino.PrescalerInfo> prescalerInfoValues;

    public final Map<Girino.Parameter, SupportLevel> parameterSupportLevels;

    public final Map<Girino.Parameter, Integer> factoryParameterValues;

    private Device(
            String id,
            String description,
            long baseFrequency,
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
        this.baseFrequency = baseFrequency;
        this.setupDelayOnReset = setupDelayOnReset;
        this.readyMessage = readyMessage;
        this.frameFormat = frameFormat;
        this.prescalerInfoValues = prescalerInfoValues;
        this.parameterSupportLevels = parameterSupportLevels;
        this.factoryParameterValues = factoryParameterValues;
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
        for (Map.Entry<Girino.Parameter, Integer> entry : factoryParameterValues.entrySet()) {
            parameters.put(entry.getKey(), entry.getValue());
        }
        return parameters;
    }
}
