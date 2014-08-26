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

import org.hihan.girinoscope.ui.Axis.GraphLabel;

@SuppressWarnings("serial")
public class GraphPane extends JPanel {

    private static final Color DIVISION_COLOR = new Color(0xbcbcbc);

    private static final Color SUB_DIVISION_COLOR = new Color(0xcdcdcd);

    private static final Color TEXT_COLOR = new Color(0x9a9a9a);

    private static final Color DATA_COLOR = Color.CYAN.darker();

    private static final Color THRESHOLD_COLOR = Color.ORANGE.darker();

    private static final Color WAIT_DURATION_COLOR = Color.GREEN.darker();

    private static final Font FONT = Font.decode(Font.MONOSPACED);

    private static final Stroke DASHED = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0,
            new float[] { 5 }, 0);

    private static final Stroke DOTTED = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0,
            new float[] { 3 }, 0);

    private static final int V_MAX = 255;

    private static final int U_MAX = 1279;

    private Stroke dataStroke = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL);

    private Axis xAxis;

    private Axis yAxis;

    private byte[] data;

    private Rectangle graphArea;

    private int threshold;

    private int waitDuration;

    private enum Rule {
        THRESHOLD_RULE, WAIT_DURATION_RULE
    }

    private Rule grabbedRule;

    public GraphPane(int initialThreshold, int initialWaitDuration) {
        threshold = initialThreshold;
        waitDuration = initialWaitDuration;
        addMouseMotionListener(new MouseMotionListener() {

            @Override
            public void mouseMoved(MouseEvent event) {
                Map<Rule, Point> anchors = new HashMap<Rule, Point>();

                Point thresholdRuleAnchorLocation = toGraphArea(U_MAX, threshold);
                SwingUtilities.convertPointToScreen(thresholdRuleAnchorLocation, GraphPane.this);
                anchors.put(Rule.THRESHOLD_RULE, thresholdRuleAnchorLocation);

                Point waitDurationRuleAnchorLocation = toGraphArea(waitDuration, V_MAX);
                SwingUtilities.convertPointToScreen(waitDurationRuleAnchorLocation, GraphPane.this);
                anchors.put(Rule.WAIT_DURATION_RULE, waitDurationRuleAnchorLocation);

                if (grabbedRule != null) {
                    boolean stillLocked = anchors.get(grabbedRule).distance(event.getLocationOnScreen()) < 16;
                    if (!stillLocked) {
                        setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                        grabbedRule = null;
                        repaint();
                    }
                }

                if (grabbedRule == null) {
                    for (Map.Entry<Rule, Point> entry : anchors.entrySet()) {
                        boolean nowLocked = entry.getValue().distance(event.getLocationOnScreen()) < 16;
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
                        GraphPane.this.threshold = Math.max(0, Math.min(newThreshold, V_MAX));
                        break;

                    case WAIT_DURATION_RULE:
                        int newWaitDuration = uv.x;
                        GraphPane.this.waitDuration = Math.max(0, Math.min(newWaitDuration, U_MAX));
                        break;

                    default:
                        throw new IllegalArgumentException(grabbedRule.name());
                    }
                    repaint();
                }
            }
        });
    }

    public void setDataStrokeWidth(float width) {
        dataStroke = new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL);
    }

    public void setCoordinateSystem(Axis xAxis, Axis yAxis) {
        this.xAxis = xAxis;
        this.yAxis = yAxis;
        repaint();
    }

    public void setData(byte[] data) {
        this.data = data;
        repaint();
    }

    public int getThreshold() {
        return threshold;
    }

    public int getWaitDuration() {
        return waitDuration;
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
                paintWaitDurationRule(g2d);
                paintThresholdRule(g2d);
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
        g.setColor(DATA_COLOR);
        Stroke defaultStroke = g.getStroke();
        g.setStroke(dataStroke);
        int u = U_MAX;
        Point previousPoint = null;
        for (byte b : data) {
            Point point = toGraphArea(u--, b & 0xFF);
            if (previousPoint != null) {
                g.drawLine(previousPoint.x, previousPoint.y, point.x, point.y);
            }
            previousPoint = point;
        }
        g.setStroke(defaultStroke);
    }

    private void paintThresholdRule(Graphics2D g) {
        g.setColor(THRESHOLD_COLOR);
        Point point = toGraphArea(U_MAX, threshold);
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
        Point point = toGraphArea(waitDuration, V_MAX);
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
        int x = (int) Math.round((U_MAX - u) * graphArea.width / U_MAX + graphArea.x);
        int y = (int) Math.round((V_MAX - v) * graphArea.height / V_MAX + graphArea.y);
        return new Point(x, y);
    }

    private Point toData(int x, int y) {
        int u = (int) Math.round(U_MAX - (x - graphArea.x) * U_MAX / graphArea.width);
        int v = (int) Math.round(V_MAX - (y - graphArea.y) * V_MAX / graphArea.height);
        return new Point(u, v);
    }
}
