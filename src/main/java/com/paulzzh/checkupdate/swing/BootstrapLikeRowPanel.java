package com.paulzzh.checkupdate.swing;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class BootstrapLikeRowPanel extends JPanel {
    private final SquareIconView iconView = new SquareIconView();
    private final TranslucentTextBox nameBox = new TranslucentTextBox();
    private final TranslucentTextBox versionBox = new TranslucentTextBox();
    private final JButton updateButton = createOutlinedButton("检查更新");
    private final JButton fixButton = createOutlinedButton("修复整合包");
    private static final int ROW_HEIGHT = 42;
    private static final int COL_WIDTH = 140;

    public BootstrapLikeRowPanel(ImageIcon icon, String name, String version) {
        setOpaque(false);
        setLayout(new GridBagLayout());

        iconView.setIcon(icon);
        nameBox.setText(name);
        versionBox.setText(version);

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.add(nameBox);
        textPanel.add(Box.createVerticalStrut(8));
        textPanel.add(versionBox);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setOpaque(false);
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
        updateButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        fixButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        buttonPanel.add(updateButton);
        buttonPanel.add(Box.createVerticalStrut(8));
        buttonPanel.add(fixButton);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.fill = GridBagConstraints.BOTH;

        gbc.gridx = 0;
        gbc.weightx = 0;
        add(iconView, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        add(textPanel, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        add(buttonPanel, gbc);
    }

    public void setIcon(ImageIcon icon) {
        iconView.setIcon(icon);
    }

    public void setNameText(String text) {
        nameBox.setText(text);
    }

    public void setVersionText(String text) {
        versionBox.setText(text);
    }

    public void setUpdateButtonAction(Runnable action) {
        updateButton.addActionListener(e -> action.run());
    }

    public void setFixButtonAction(Runnable action) {
        fixButton.addActionListener(e -> action.run());
    }

    private static JButton createOutlinedButton(String text) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setForeground(new Color(0x0D6EFD));
        button.setBorder(BorderFactory.createLineBorder(new Color(0x0D6EFD), 1, true));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(COL_WIDTH, ROW_HEIGHT));
        button.setMinimumSize(new Dimension(COL_WIDTH, ROW_HEIGHT));
        button.setMaximumSize(new Dimension(COL_WIDTH, ROW_HEIGHT));

        // 让文字在 42 高度里居中
        button.setMargin(new Insets(0, 12, 0, 12));
        return button;
    }

    private static class TranslucentTextBox extends JPanel {
        private final JLabel label = new JLabel();

        TranslucentTextBox() {
            setOpaque(false);
            setLayout(new BorderLayout());
            setBorder(new EmptyBorder(8, 12, 8, 12));

            label.setFont(label.getFont().deriveFont(Font.BOLD, 18f));
            label.setForeground(Color.BLACK);
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setVerticalAlignment(SwingConstants.CENTER);

            add(label, BorderLayout.CENTER);

            setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW_HEIGHT));
            setPreferredSize(new Dimension(220, ROW_HEIGHT));
        }

        public void setText(String text) {
            label.setText(text);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(255, 255, 255, 153)); // 60% 白色
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static class SquareIconView extends JComponent {
        private ImageIcon icon;
        private int baseSize = 80;

        public void setIcon(ImageIcon icon) {
            this.icon = icon;
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(baseSize, baseSize);
        }

        @Override
        public Dimension getMinimumSize() {
            return new Dimension(40, 40);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            if (icon == null) {
                return;
            }

            int size = Math.min(getWidth(), getHeight());
            int x = (getWidth() - size) / 2;
            int y = (getHeight() - size) / 2;

            int iw = icon.getIconWidth();
            int ih = icon.getIconHeight();
            if (iw <= 0 || ih <= 0) {
                return;
            }

            double scale = Math.min((double) size / iw, (double) size / ih);
            int dw = Math.max(1, (int) Math.round(iw * scale));
            int dh = Math.max(1, (int) Math.round(ih * scale));
            int dx = x + (size - dw) / 2;
            int dy = y + (size - dh) / 2;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.drawImage(icon.getImage(), dx, dy, dw, dh, this);

            g2.dispose();
        }
    }
}
