package org.hihan.girinoscope.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.swing.border.LineBorder;

@SuppressWarnings("serial")
public class AboutDialog extends JDialog {

    String text = "<html>"
            + "<h1>Girinoscope</h1>"
            + "A simple graphical user interface for<br/>"
            + "<a href='http://www.instructables.com/id/Girino-Fast-Arduino-Oscilloscope/'>Girino, a Fast Arduino Oscilloscope</a>."
            + "</html>";

    public AboutDialog(JFrame owner) {
        super(owner, true);
        setBackground(Color.WHITE);
        setLayout(new BorderLayout());
        JLabel label = new JLabel(new ImageIcon(getClass().getResource("images/logo-girino.png")));
        label.setText(text);
        label.setOpaque(true);
        label.setBackground(Color.WHITE);
        label.setBorder(new LineBorder(Color.WHITE, 16));
        label.setHorizontalTextPosition(SwingConstants.CENTER);
        label.setVerticalTextPosition(SwingConstants.BOTTOM);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        add(label);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
        
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                dispose();
            }
        });
    }
}
