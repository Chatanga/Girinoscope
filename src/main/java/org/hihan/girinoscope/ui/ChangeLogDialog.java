package org.hihan.girinoscope.ui;

import org.hihan.girinoscope.utils.ui.DialogHelper;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Point;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.WindowConstants;

@SuppressWarnings("serial")
public class ChangeLogDialog extends JDialog {

    public ChangeLogDialog(JFrame owner) {
        super(owner, true);
        super.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        super.setLayout(new BorderLayout());
        super.setBackground(Color.WHITE);

        try {
            HtmlPane htmlPane = new HtmlPane(ChangeLogDialog.class.getResource("CHANGELOG.html"), System.getProperties());
            htmlPane.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            htmlPane.setOpaque(true);

            final JScrollPane scrollPane = new JScrollPane(
                    htmlPane,
                    ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

            super.addWindowListener(new WindowAdapter() {
                @Override
                public void windowOpened(WindowEvent arg0) {
                    scrollPane.getViewport().setViewPosition(new Point());
                }
            });

            super.add(scrollPane, BorderLayout.CENTER);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        super.pack();
        super.setSize(super.getWidth(), 400);
        super.setLocationRelativeTo(owner);
        super.setResizable(false);

        DialogHelper.installEscapeCloseOperation(ChangeLogDialog.this);
    }
}
