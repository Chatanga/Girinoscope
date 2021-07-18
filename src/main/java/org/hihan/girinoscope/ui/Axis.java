package org.hihan.girinoscope.ui;

import org.hihan.girinoscope.utils.Settings;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class Axis {

    public static class Builder {

        private static final double DEFAULT_START_VALUE = -2.5;

        private static final double DEFAULT_END_VALUE = 2.5;

        private static final double DEFAULT_INCREMENT = 0.5;

        private static final String DEFAULT_FORMAT = "#,##0.00 V";

        private double startValue = DEFAULT_START_VALUE;

        private double endValue = DEFAULT_END_VALUE;

        private Double increment = DEFAULT_INCREMENT;

        private String format = DEFAULT_FORMAT;

        public void load(Settings settings, String prefix) {
            startValue = settings.get(prefix + "startValue", DEFAULT_START_VALUE);
            endValue = settings.get(prefix + "endValue", DEFAULT_END_VALUE);
            increment = settings.get(prefix + "increment", DEFAULT_INCREMENT);
            format = settings.get(prefix + "format", DEFAULT_FORMAT);
        }

        public void save(Settings settings, String prefix) {
            settings.put(prefix + "startValue", startValue);
            settings.put(prefix + "endValue", endValue);
            settings.put(prefix + "increment", increment);
            settings.put(prefix + "format", format);
        }

        public double getStartValue() {
            return startValue;
        }

        public void setStartValue(double startValue) {
            this.startValue = startValue;
        }

        public double getEndValue() {
            return endValue;
        }

        public void setEndValue(double endValue) {
            this.endValue = endValue;
        }

        public double getIncrement() {
            if (increment != null) {
                return increment;
            } else {
                return chooseIncrement(startValue, endValue);
            }
        }

        public void setIncrement(double increment) {
            this.increment = increment;
        }

        public String getFormat() {
            return format;
        }

        public void setFormat(String format) {
            this.format = format;
        }

        public Axis build() {
            return new Axis(getStartValue(), getEndValue(), getIncrement(), getFormat());
        }
    }

    public static class GraphLabel {

        private final String label;

        private Rectangle bounds;

        private GraphLabel(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        public Rectangle getBounds() {
            return bounds;
        }
    }

    private double fraction;

    private List<GraphLabel> graphLabels;

    private Rectangle maxBounds;

    public Axis(double startValue, double endValue, String format) {
        this(startValue, endValue, chooseIncrement(startValue, endValue), format);
    }

    private static double chooseIncrement(double startValue, double endValue) {
        double length = endValue - startValue;
        double increment = Math.pow(10, Math.round(Math.log10(length / 10)));
        double fraction = length / increment;
        if (fraction < 5) {
            increment /= 2;
        }
        if (fraction > 10) {
            increment *= 2;
        }
        return increment;
    }

    public Axis(double startValue, double endValue, double increment, String format) {
        fraction = (endValue - startValue) / increment;
        if (Double.isInfinite(fraction) || Double.isNaN(fraction)) {
            throw new IllegalArgumentException("Unproper increment: " + increment);
        }
        graphLabels = new ArrayList<>();
        for (double value = startValue; value <= endValue; value += increment) {
            graphLabels.add(new GraphLabel(new DecimalFormat(format).format(value)));
        }
    }

    public void complete(Graphics2D g, Font font) {
        if (maxBounds == null) {
            maxBounds = new Rectangle(0, 0, 0, 0);
            for (GraphLabel graphLabel : graphLabels) {
                if (graphLabel.bounds == null) {
                    graphLabel.bounds = g.getFontMetrics(font).getStringBounds(graphLabel.label, g).getBounds();
                }
                maxBounds = maxBounds.union(graphLabel.bounds);
            }
        }
    }

    public double getFraction() {
        return fraction;
    }

    public GraphLabel[] graphLabels() {
        return graphLabels.toArray(new GraphLabel[0]);
    }

    public Rectangle getMaxBounds() {
        return maxBounds;
    }
}
