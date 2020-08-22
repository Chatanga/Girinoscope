package org.hihan.girinoscope.ui;

import com.fazecast.jSerialComm.SerialPort;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.hihan.girinoscope.comm.Device;
import org.hihan.girinoscope.comm.Girino;

// Beware of the distinction between a property and its (parameter) value.
// Plus, information loss.
public class DeviceModel {

    public static final String DEVICE_PROPERTY_NAME = "device";

    public static final String PORT_PROPERTY_NAME = "port";

    public static final String VOLTAGE_REFERENCE_PROPERTY_NAME = "voltageReference";

    public static final String PRESCALER_INFO_PROPERTY_NAME = "prescalerInfo";

    public static final String TRIGGER_EVENT_MODE_PROPERTY_NAME = "triggerEventMode";

    public static final String THRESHOLD_PROPERTY_NAME = "threshold";

    public static final String WAIT_DURATION_PROPERTY_NAME = "waitDuration";

    private final PropertyChangeSupport support;

    /*
     * The selected device on which the Girino firmware is running (could be
     * different from the one currently configured for the Girino). Its value
     * should only be changed using {@link #setDevice}.
     */
    private Device device;

    /*
     * The currently selected serial port used to connect to the Girino
     * hardware.
     */
    private SerialPort port;

    /*
     * The edited Girino settings (could be different from the ones uploaded to
     * the Girino hardware).
     */
    final Map<Girino.Parameter, Integer> parameters;

    public DeviceModel() {
        support = new PropertyChangeSupport(this);
        parameters = new EnumMap<>(Girino.Parameter.class);
    }

    public DeviceModel(DeviceModel model) {
        support = new PropertyChangeSupport(this);
        device = model.device;
        port = model.port;
        parameters = new EnumMap<>(model.parameters);
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        support.removePropertyChangeListener(listener);
    }

    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        support.addPropertyChangeListener(propertyName, listener);
    }

    // Add a listener which will be automatically removed when the device change.
    public void addTemporaryPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        if (!propertyName.equals(DEVICE_PROPERTY_NAME)) {
            PropertyChangeListener realListener = new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent event) {
                    if (DEVICE_PROPERTY_NAME.equals(event.getPropertyName())) {
                        removePropertyChangeListener(this);
                    } else if (propertyName.equals(event.getPropertyName())) {
                        listener.propertyChange(event);
                    }
                }
            };
            support.addPropertyChangeListener(realListener);
        } else {
            throw new IllegalArgumentException();
        }
    }

    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        support.removePropertyChangeListener(propertyName, listener);
    }

    public Device getDevice() {
        return device;
    }

    public void setDevice(Device device) {
        // TODO Subordination relation is not great here.
        parameters.clear();
        device.getDefaultParameters(parameters);
        support.firePropertyChange(DEVICE_PROPERTY_NAME, this.device, this.device = device);
    }

    public SerialPort getPort() {
        return port;
    }

    public void setPort(SerialPort port) {
        support.firePropertyChange(PORT_PROPERTY_NAME, this.port, this.port = port);
    }

    public boolean isVoltageReferenceSet(Girino.VoltageReference voltageReference) {
        return Objects.equals(parameters.get(Girino.Parameter.VOLTAGE_REFERENCE), voltageReference.value);
    }

    public Girino.VoltageReference getVoltageReference() {
        throw new UnsupportedOperationException("The Girino protocol offers no mean to retrieve the voltage reference.");
    }

    public void setVoltageReference(Girino.VoltageReference voltageReference) {
        int oldVoltageReferenceValue = parameters.get(Girino.Parameter.PRESCALER);
        parameters.put(Girino.Parameter.VOLTAGE_REFERENCE, voltageReference.value);
        support.firePropertyChange(VOLTAGE_REFERENCE_PROPERTY_NAME, oldVoltageReferenceValue, voltageReference);
    }

    public boolean isPrescalerInfoSet(Girino.PrescalerInfo prescalerInfo) {
        return Objects.equals(parameters.get(Girino.Parameter.PRESCALER), prescalerInfo.value);
    }

    public Girino.PrescalerInfo getPrescalerInfo() {
        int prescalerInfoValue = parameters.get(Girino.Parameter.PRESCALER);
        return device.getPrescalerInfoValues().stream()
                .filter(p -> p.value == prescalerInfoValue)
                .findFirst()
                .orElse(null);
    }

    public void setPrescalerInfo(Girino.PrescalerInfo prescalerInfo) {
        int oldPrescalerInfoValue = parameters.get(Girino.Parameter.PRESCALER);
        parameters.put(Girino.Parameter.PRESCALER, prescalerInfo.value);
        support.firePropertyChange(PRESCALER_INFO_PROPERTY_NAME, oldPrescalerInfoValue, prescalerInfo.value);
    }

    public boolean isTriggerEventModeSet(Girino.TriggerEventMode triggerEventMode) {
        return Objects.equals(parameters.get(Girino.Parameter.TRIGGER_EVENT), triggerEventMode.value);
    }

    public Girino.TriggerEventMode getTriggerEventMode() {
        int triggerEventValue = parameters.get(Girino.Parameter.TRIGGER_EVENT);
        return Stream.of(Girino.TriggerEventMode.values())
                .filter(p -> p.value == triggerEventValue)
                .findFirst()
                .orElse(null);
    }

    public void setTriggerEventMode(Girino.TriggerEventMode triggerEventMode) {
        int oldTriggerEventModeValue = parameters.get(Girino.Parameter.TRIGGER_EVENT);
        parameters.put(Girino.Parameter.TRIGGER_EVENT, triggerEventMode.value);
        support.firePropertyChange(TRIGGER_EVENT_MODE_PROPERTY_NAME, oldTriggerEventModeValue, triggerEventMode.value);
    }

    public int getThreshold() {
        return parameters.get(Girino.Parameter.THRESHOLD);
    }

    public void setThreshold(int threshold) {
        int oldThreshold = parameters.get(Girino.Parameter.THRESHOLD);
        parameters.put(Girino.Parameter.THRESHOLD, threshold);
        support.firePropertyChange(THRESHOLD_PROPERTY_NAME, oldThreshold, threshold);
    }

    public int getWaitDuration() {
        return parameters.get(Girino.Parameter.WAIT_DURATION);
    }

    public void setWaitDuration(int waitDuration) {
        int oldWaitDuration = parameters.get(Girino.Parameter.WAIT_DURATION);
        parameters.put(Girino.Parameter.WAIT_DURATION, waitDuration);
        support.firePropertyChange(WAIT_DURATION_PROPERTY_NAME, oldWaitDuration, waitDuration);
    }

    public Map<Girino.Parameter, Integer> toParameters() {
        return Collections.unmodifiableMap(parameters);
    }

    // TODO port ou port.get...() ?
    //@Generated(value = "NetBeans")
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 59 * hash + Objects.hashCode(this.device);
        hash = 59 * hash + Objects.hashCode(this.port);
        hash = 59 * hash + Objects.hashCode(this.parameters);
        return hash;
    }

    //@Generated(value = "NetBeans")
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final DeviceModel other = (DeviceModel) obj;
        if (!Objects.equals(this.device, other.device)) {
            return false;
        }
        if (!Objects.equals(this.port, other.port)) {
            return false;
        }
        if (!Objects.equals(this.parameters, other.parameters)) {
            return false;
        }
        return true;
    }
}
