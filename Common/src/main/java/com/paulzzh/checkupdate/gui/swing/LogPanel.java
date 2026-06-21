package com.paulzzh.checkupdate.gui.swing;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class LogPanel extends JPanel {
    private static final Color PANEL_BG = new Color(255, 255, 255, 102); // 40% 白色
    private final JTextArea textArea = new JTextArea();

    public LogPanel() {
        setOpaque(false);
        setLayout(new BorderLayout());

        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setOpaque(false);
        textArea.setBackground(new Color(0, 0, 0, 0));
        textArea.setForeground(Color.BLACK);
        textArea.setBorder(new EmptyBorder(8, 8, 8, 8));

        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getViewport().setBackground(new Color(0, 0, 0, 0));
        scrollPane.setBackground(new Color(0, 0, 0, 0));

        add(scrollPane, BorderLayout.CENTER);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(PANEL_BG);
        g2.fillRect(0, 0, getWidth() - 20, getHeight() - 20);
        g2.dispose();
        super.paintComponent(g);
    }

    public void appendLog(String text) {
        SwingUtilities.invokeLater(() -> {
            textArea.append(text + "\n");
            textArea.setCaretPosition(textArea.getDocument().getLength());
        });
    }
}