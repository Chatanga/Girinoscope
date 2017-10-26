package org.hihan.girinoscope.ui;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class Axis {

    public static class Builder {

	private double startValue = -2.5;

	private double endValue = 2.5;

	private Double increment = 0.5;

	private String format = "#,##0.00 V";

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
	    return increment != null ? increment : chooseIncrement(startValue, endValue);
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

	private String label;

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
	graphLabels = new ArrayList<Axis.GraphLabel>();
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
