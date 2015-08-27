package org.hihan.girinoscope.ui;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;

import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JToolBar;
import javax.swing.UIManager;

@SuppressWarnings("serial")
public class StatusBar extends JToolBar {

    private JLabel label = new JLabel();

    public StatusBar() {
        setFloatable(false);
        add(Box.createVerticalStrut(16));
        add(label);
    }

    public void setText(String text) {
        label.setText(text);
    }

    @Override
    protected void paintComponent(Graphics g) {
        String laf = UIManager.getLookAndFeel().getClass().getName();
        /*
         * On other LaF, notably Nimbus, toolbar painting take the component
         * location into account (North or South in our case). It is not the
         * case with the GTK+ LaF which need our help here.
         */
        boolean mirrored = "com.sun.java.swing.plaf.gtk.GTKLookAndFeel".equals(laf);
        super.paintComponent(mirrored ? createVerticalMirrorGraphics(g) : g);
    }

    private Graphics createVerticalMirrorGraphics(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        AffineTransform transform = AffineTransform.getScaleInstance(1, -1);
        transform.translate(0, -getHeight());
        g2d.transform(transform);
        return g2d;
    }
}
