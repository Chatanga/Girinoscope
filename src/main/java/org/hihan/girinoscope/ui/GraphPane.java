package org.hihan.girinoscope.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.hihan.girinoscope.comm.FrameFormat;
import org.hihan.girinoscope.comm.Girino;
import org.hihan.girinoscope.ui.Axis.GraphLabel;

@SuppressWarnings("serial")
public class GraphPane extends JPanel {

    private static final Color DIVISION_COLOR = new Color(0xbcbcbc);

    private static final Color SUB_DIVISION_COLOR = new Color(0xcdcdcd);

    private static final Color TEXT_COLOR = new Color(0x9a9a9a);

    private static final Color[] DATA_COLORS = {Color.CYAN.darker(), Color.ORANGE};

    private static final Color XY_DATA_COLOR = Color.MAGENTA;

    private static final Color THRESHOLD_COLOR = Color.ORANGE.darker();

    private static final Color WAIT_DURATION_COLOR = Color.GREEN.darker();

    private static final Font FONT = Font.decode(Font.MONOSPACED);

    private static final Stroke DASHED = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{5}, 0);

    private static final Stroke DOTTED = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{3}, 0);

    private Stroke dataStroke = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL);

    private Axis xAxis, xAxisBackup;

    private Axis yAxis;

    private FrameFormat frameFormat;

    private int uMax, uMaxBackup;

    private int vMax;

    private byte[] data;

    private Rectangle graphArea;

    private boolean triggerEnabled;

    private int threshold;

    private Girino.ChannelCompositionMode channelCompositionMode;

    private int waitDuration;

    private enum Rule {

        THRESHOLD_RULE, WAIT_DURATION_RULE
    }

    private Rule grabbedRule;

    public GraphPane() {
        super.addMouseMotionListener(new MouseMotionListener() {

            private final int HAND_RADIUS = 16;

            @Override
            public void mouseMoved(MouseEvent event) {
                Map<Rule, Point> anchors = new HashMap<>();

                if (triggerEnabled) {
                    Point thresholdRuleAnchorLocation = toGraphArea(uMax, threshold);
                    SwingUtilities.convertPointToScreen(thresholdRuleAnchorLocation, GraphPane.this);
                    anchors.put(Rule.THRESHOLD_RULE, thresholdRuleAnchorLocation);
                }

                if (!isXY(channelCompositionMode)) {
                    Point waitDurationRuleAnchorLocation = toGraphArea(waitDuration, vMax);
                    SwingUtilities.convertPointToScreen(waitDurationRuleAnchorLocation, GraphPane.this);
                    anchors.put(Rule.WAIT_DURATION_RULE, waitDurationRuleAnchorLocation);
                }

                if (grabbedRule != null) {
                    boolean stillLocked = anchors.get(grabbedRule).distance(event.getLocationOnScreen()) < HAND_RADIUS;
                    if (!stillLocked) {
                        setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                        grabbedRule = null;
                        repaint();
                    }
                }

                if (grabbedRule == null) {
                    for (Map.Entry<Rule, Point> entry : anchors.entrySet()) {
                        boolean nowLocked = entry.getValue().distance(event.getLocationOnScreen()) < HAND_RADIUS;
                        if (nowLocked) {
                            setCursor(new Cursor(Cursor.HAND_CURSOR));
                            grabbedRule = entry.getKey();
                            repaint();
                            return;
                        }
                    }
                }
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                if (grabbedRule != null) {
                    Point graphAreaPosition = event.getLocationOnScreen();
                    SwingUtilities.convertPointFromScreen(graphAreaPosition, GraphPane.this);
                    Point uv = toData(graphAreaPosition.x, graphAreaPosition.y);
                    switch (grabbedRule) {

                        case THRESHOLD_RULE:
                            int newThreshold = uv.y;
                            setThreshold(Math.max(0, Math.min(newThreshold, vMax)));
                            break;

                        case WAIT_DURATION_RULE:
                            int newWaitDuration = uv.x;
                            setWaitDuration(Math.max(0, Math.min(newWaitDuration, uMax)));
                            break;

                        default:
                            throw new IllegalArgumentException(grabbedRule.name());
                    }
                    repaint();
                }
            }
        });
    }

    public void setTriggerEnabled(boolean enabled) {
        this.triggerEnabled = enabled;
        repaint();
    }

    public void setThreshold(int threshold) {
        this.threshold = threshold;
        repaint();
    }

    public void setChannelCompositionMode(Girino.ChannelCompositionMode channelCompositionMode) {
        if (isXY(this.channelCompositionMode) != isXY(channelCompositionMode)) {
            if (isXY(channelCompositionMode)) {
                xAxisBackup = xAxis;
                xAxis = yAxis;
                uMaxBackup = uMax;
                uMax = vMax;
            } else {
                xAxis = xAxisBackup;
                uMax = uMaxBackup;
            }
        }
        this.channelCompositionMode = channelCompositionMode;
        repaint();
    }

    public void setWaitDuration(int waitDuration) {
        this.waitDuration = waitDuration;
        repaint();
    }

    public void setDataStrokeWidth(float width) {
        dataStroke = new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL);
        repaint();
    }

    public void setCoordinateSystem(Axis xAxis, Axis yAxis) {
        if (isXY(channelCompositionMode)) {
            xAxisBackup = xAxis;
            xAxis = yAxis;
        } else {
            this.xAxis = xAxis;
        }
        this.yAxis = yAxis;
        repaint();
    }

    public void setXCoordinateSystem(Axis xAxis) {
        if (isXY(channelCompositionMode)) {
            xAxisBackup = xAxis;
        } else {
            this.xAxis = xAxis;
            repaint();
        }
    }

    public void setYCoordinateSystem(Axis yAxis) {
        if (isXY(channelCompositionMode)) {
            xAxis = yAxis;
        }
        this.yAxis = yAxis;
        repaint();
    }

    public void setFrameFormat(FrameFormat frameFormat) {
        this.frameFormat = frameFormat;
        uMax = frameFormat.sampleCount - 1;
        vMax = frameFormat.sampleMaxValue;
        if (isXY(channelCompositionMode)) {
            uMaxBackup = uMax;
            uMax = vMax;
        }
    }

    public void setData(byte[] data) {
        this.data = data;
        repaint();
    }

    public byte[] getData() {
        return data;
    }

    public int[] getValues() {
        return frameFormat.readValues(data);
    }

    public int getThreshold() {
        return threshold;
    }

    public Girino.ChannelCompositionMode getChannelCompositionMode() {
        return channelCompositionMode;
    }

    public int getWaitDuration() {
        return waitDuration;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;

        // Performance issues on Linux without an explicit -Dsun.java2d.opengl=true.
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);

        int w = getWidth();
        int h = getHeight();

        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, w, h);

        if (xAxis != null && yAxis != null) {
            xAxis.complete(g2d, FONT);
            yAxis.complete(g2d, FONT);

            graphArea = new Rectangle(16, 16, w - 32, h - 32);
            Insets labelInsets = new Insets(2, 12, 0, 0);
            graphArea.width -= yAxis.getMaxBounds().width + labelInsets.left + labelInsets.right;
            graphArea.height -= xAxis.getMaxBounds().height + labelInsets.top + labelInsets.bottom;

            if (w > 0 && h > 0) {
                paintXAxis(g2d, labelInsets);
                paintYAxis(g2d, labelInsets);
                if (data != null) {
                    paintData(g2d);
                }
                if (!isXY(channelCompositionMode)) {
                    paintWaitDurationRule(g2d);
                }
                if (triggerEnabled) {
                    paintThresholdRule(g2d);
                }
            }
        }
    }

    private void paintXAxis(Graphics2D g, Insets labelInsets) {
        g.translate(graphArea.x, graphArea.y);
        GraphLabel[] xLabels = xAxis.graphLabels();
        for (int i = 0; i < xLabels.length; ++i) {
            GraphLabel xLabel = xLabels[i];
            int xOffset = (int) (i * graphArea.width / xAxis.getFraction());

            Stroke defaultStroke = g.getStroke();

            g.setStroke(i % (xLabels.length / 2) != 0 ? DASHED : defaultStroke);
            g.setColor(i % (xLabels.length - 1) == 0 ? DIVISION_COLOR : SUB_DIVISION_COLOR);
            g.drawLine(xOffset, 0, xOffset, graphArea.height);

            g.setStroke(defaultStroke);
            g.setColor(TEXT_COLOR);
            Rectangle bounds = xLabel.getBounds();
            int dx;
            if (i == 0) {
                dx = 0;
            } else {
                dx = -bounds.width / 2;
            }
            int dy = labelInsets.top;
            g.drawString(xLabel.getLabel(), xOffset + dx, graphArea.height + bounds.height + dy);
        }
        g.drawLine(graphArea.width, 0, graphArea.width, graphArea.height);

        g.translate(-graphArea.x, -graphArea.y);
    }

    private void paintYAxis(Graphics2D g, Insets labelInsets) {
        g.translate(graphArea.x, graphArea.y);
        GraphLabel[] yLabels = yAxis.graphLabels();
        for (int i = 0; i < yLabels.length; ++i) {
            GraphLabel yLabel = yLabels[yLabels.length - i - 1];
            int yOffset = (int) (i * graphArea.height / yAxis.getFraction());

            Stroke defaultStroke = g.getStroke();

            g.setStroke(i % (yLabels.length / 2) != 0 ? DASHED : defaultStroke);
            g.setColor(i % (yLabels.length - 1) == 0 ? DIVISION_COLOR : SUB_DIVISION_COLOR);
            g.drawLine(0, yOffset, graphArea.width, yOffset);

            g.setStroke(defaultStroke);
            g.setColor(TEXT_COLOR);
            Rectangle bounds = yLabel.getBounds();
            int dy = bounds.height / 2;
            int dx = labelInsets.left;
            g.drawString(yLabel.getLabel(), graphArea.width + dx, yOffset + dy);
        }
        g.drawLine(0, graphArea.height, graphArea.width, graphArea.height);
        g.translate(-graphArea.x, -graphArea.y);
    }

    private void paintData(Graphics2D g) {
        if (isXY(channelCompositionMode)) {
            paintDataXY(g);
        } else {
            paintMultiChannelData(g, channelCompositionMode.value);
        }
    }

    private void paintMultiChannelData(Graphics2D g, int channelCount) {
        Stroke defaultStroke = g.getStroke();
        g.setStroke(dataStroke);
        int[] values = getValues();
        for (int channel = 0; channel < channelCount; ++channel) {
            g.setColor(DATA_COLORS[channel % DATA_COLORS.length]);
            int u = uMax;
            Point previousPoint = null;
            for (int i = channel; i < values.length; i += channelCount) {
                int v = values[i];
                assert v >= 0 && v <= frameFormat.sampleMaxValue;
                Point point = toGraphArea(u, v);
                u -= channelCount;
                if (previousPoint != null) {
                    g.drawLine(previousPoint.x, previousPoint.y, point.x, point.y);
                }
                previousPoint = point;
            }
        }
        g.setStroke(defaultStroke);
    }

    private void paintDataXY(Graphics2D g) {
        Stroke defaultStroke = g.getStroke();
        g.setStroke(dataStroke);
        g.setColor(XY_DATA_COLOR);
        Point previousPoint = null;
        int[] values = getValues();
        for (int i = 0; i < values.length; i += 2) {
            int u = uMax - values[i];
            int v = values[i + 1];
            Point point = toGraphArea(u, v);
            if (previousPoint != null) {
                g.drawLine(previousPoint.x, previousPoint.y, point.x, point.y);
            }
            previousPoint = point;
        }
        g.setStroke(defaultStroke);
    }

    private void paintThresholdRule(Graphics2D g) {
        g.setColor(THRESHOLD_COLOR);
        Point point = toGraphArea(uMax, threshold);
        Stroke defaultStroke = g.getStroke();
        g.setStroke(DOTTED);
        g.drawLine(point.x, point.y, point.x + graphArea.width, point.y);
        g.setStroke(defaultStroke);

        Graphics2D gg = (Graphics2D) g.create();
        gg.translate(point.x, point.y);
        gg.rotate(Math.PI / 4);
        if (grabbedRule == Rule.THRESHOLD_RULE) {
            gg.fill3DRect(-4, -4, 9, 9, true);
        } else {
            gg.fill3DRect(-3, -3, 7, 7, true);
        }
    }

    private void paintWaitDurationRule(Graphics2D g) {
        g.setColor(WAIT_DURATION_COLOR);
        Point point = toGraphArea(waitDuration, vMax);
        Stroke defaultStroke = g.getStroke();
        g.setStroke(DOTTED);
        g.drawLine(point.x, point.y, point.x, point.y + graphArea.height);
        g.setStroke(defaultStroke);

        Graphics2D gg = (Graphics2D) g.create();
        gg.translate(point.x, point.y);
        gg.rotate(Math.PI / 4);
        if (grabbedRule == Rule.WAIT_DURATION_RULE) {
            gg.fill3DRect(-4, -4, 9, 9, true);
        } else {
            gg.fill3DRect(-3, -3, 7, 7, true);
        }
    }

    private Point toGraphArea(int u, int v) {
        int x = Math.round((uMax - u) * graphArea.width / uMax + graphArea.x);
        int y = Math.round((vMax - v) * graphArea.height / vMax + graphArea.y);
        return new Point(x, y);
    }

    private Point toData(int x, int y) {
        int u = Math.round(uMax - (x - graphArea.x) * uMax / graphArea.width);
        int v = Math.round(vMax - (y - graphArea.y) * vMax / graphArea.height);
        return new Point(u, v);
    }

    private static boolean isXY(Girino.ChannelCompositionMode channelCompositionMode) {
        return channelCompositionMode == Girino.ChannelCompositionMode.XY;
    }
}
