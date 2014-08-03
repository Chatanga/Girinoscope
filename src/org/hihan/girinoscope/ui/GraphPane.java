package org.hihan.girinoscope.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;

import javax.swing.JPanel;

import org.hihan.girinoscope.ui.Axis.GraphLabel;

@SuppressWarnings("serial")
public class GraphPane extends JPanel {

    private Color DIVISION_COLOR = new Color(0xbcbcbc);

    private Color SUB_DIVISION_COLOR = new Color(0xcdcdcd);

    private Color TEXT_COLOR = new Color(0x9a9a9a);

    private Color DATA_COLOR = Color.CYAN.darker();

    private Font font = Font.decode(Font.MONOSPACED);

    private Stroke dashed = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[] { 5 }, 0);

    private Axis xAxis;

    private Axis yAxis;

    private byte[] data;

    public void setCoordinateSystem(Axis xAxis, Axis yAxis) {
        this.xAxis = xAxis;
        this.yAxis = yAxis;
        repaint();
    }

    public void setData(byte[] data) {
        this.data = data;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);

        int w = getWidth();
        int h = getHeight();

        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, w, h);

        if (xAxis != null && yAxis != null) {
            xAxis.complete(g2d, font);
            yAxis.complete(g2d, font);

            Insets labelInsets = new Insets(2, 12, 0, 0);

            g2d.translate(16, 16);
            w -= 32 + yAxis.getMaxBounds().width + labelInsets.left + labelInsets.right;
            h -= 32 + xAxis.getMaxBounds().height + labelInsets.top + labelInsets.bottom;

            if (w > 0 && h > 0) {
                paintXAxis(g2d, w, h, labelInsets);
                paintYAxis(g2d, w, h, labelInsets);
                if (data != null) {
                    paintData(g2d, w, h);
                }
            }
        }
    }

    private void paintXAxis(Graphics2D g, int w, int h, Insets labelInsets) {
        GraphLabel[] xLabels = xAxis.graphLabels();
        for (int i = 0; i < xLabels.length; ++i) {
            GraphLabel xLabel = xLabels[i];
            int xOffset = i * w / (xLabels.length - 1);

            Stroke defaultStroke = g.getStroke();

            g.setStroke(i % (xLabels.length / 2) != 0 ? dashed : defaultStroke);
            g.setColor(i % (xLabels.length - 1) == 0 ? DIVISION_COLOR : SUB_DIVISION_COLOR);
            g.drawLine(xOffset, 0, xOffset, h);

            g.setStroke(defaultStroke);
            g.setColor(TEXT_COLOR);
            Rectangle bounds = xLabel.getBounds();
            int dx;
            if (i == 0) {
                dx = 0;
            } else if (i + 1 == xLabels.length) {
                dx = -bounds.width;
            } else {
                dx = -bounds.width / 2;
            }
            int dy = labelInsets.top;
            g.drawString(xLabel.getLabel(), xOffset + dx, h + bounds.height + dy);
        }
    }

    private void paintYAxis(Graphics2D g, int w, int h, Insets labelInsets) {
        GraphLabel[] yLabels = yAxis.graphLabels();
        for (int i = 0; i < yLabels.length; ++i) {
            GraphLabel yLabel = yLabels[yLabels.length - i - 1];
            int yOffset = i * h / (yLabels.length - 1);

            Stroke defaultStroke = g.getStroke();

            g.setStroke(i % (yLabels.length / 2) != 0 ? dashed : defaultStroke);
            g.setColor(i % (yLabels.length - 1) == 0 ? DIVISION_COLOR : SUB_DIVISION_COLOR);
            g.drawLine(0, yOffset, w, yOffset);

            g.setStroke(defaultStroke);
            g.setColor(TEXT_COLOR);
            Rectangle bounds = yLabel.getBounds();
            int dy;
            if (i == 0) {
                dy = bounds.height;
            } else if (i + 1 == yLabels.length) {
                dy = 0;
            } else {
                dy = bounds.height / 2;
            }
            int dx = labelInsets.left;
            g.drawString(yLabel.getLabel(), w + dx, yOffset + dy);
        }
    }

    private void paintData(Graphics2D g, int w, int h) {
        g.setColor(DATA_COLOR);
        double yMax = 255;
        double xMax = data.length;
        double x = 0;
        Point previousPoint = null;
        for (byte b : data) {
            double y = yMax - (b & 0xFF);
            Point point = new Point((int) Math.round(x * w / xMax), (int) Math.round(y * h / yMax));
            if (previousPoint != null) {
                g.drawLine(previousPoint.x, previousPoint.y, point.x, point.y);
            }
            previousPoint = point;
            x += 1.0;
        }
    }
}
