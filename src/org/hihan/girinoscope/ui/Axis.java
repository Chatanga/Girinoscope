package org.hihan.girinoscope.ui;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

public class Axis {

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

    private List<GraphLabel> graphLabels;

    private Rectangle maxBounds;

    public Axis(double startValue, double endValue, String format, int divisionCount) {
        if (divisionCount < 0) {
            throw new IllegalArgumentException();
        }

        graphLabels = new ArrayList<Axis.GraphLabel>();
        double l = endValue - startValue;
        for (int i = 0; i < divisionCount; ++i) {
            double value;
            if (i == 0) {
                value = startValue;
            } else if (i + 1 == divisionCount) {
                value = endValue;
            } else {
                value = startValue + i * l / (divisionCount - 1);
            }
            graphLabels.add(new GraphLabel(String.format(format, value)));
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

    public GraphLabel[] graphLabels() {
        return graphLabels.toArray(new GraphLabel[0]);
    }

    public Rectangle getMaxBounds() {
        return maxBounds;
    }
}
