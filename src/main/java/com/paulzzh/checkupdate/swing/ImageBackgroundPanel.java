package com.paulzzh.checkupdate.swing;

import javax.swing.*;
import java.awt.*;

public class ImageBackgroundPanel extends JPanel {
    private Image backgroundImage;

    public ImageBackgroundPanel() {
        setOpaque(true);
    }

    public ImageBackgroundPanel(Image backgroundImage) {
        this.backgroundImage = backgroundImage;
        setOpaque(true);
    }

    public Image getBackgroundImage() {
        return backgroundImage;
    }

    public void setBackgroundImage(Image backgroundImage) {
        this.backgroundImage = backgroundImage;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (backgroundImage == null) {
            return;
        }

        int panelW = getWidth();
        int panelH = getHeight();

        int imgW = backgroundImage.getWidth(this);
        int imgH = backgroundImage.getHeight(this);

        if (imgW <= 0 || imgH <= 0) {
            return;
        }

        // cover 模式关键：取 max
        double scaleX = (double) panelW / imgW;
        double scaleY = (double) panelH / imgH;
        double scale = Math.max(scaleX, scaleY);

        int drawW = (int) Math.round(imgW * scale);
        int drawH = (int) Math.round(imgH * scale);

        // 居中（可能为负数，用于裁剪）
        int x = (panelW - drawW) / 2;
        int y = (panelH - drawH) / 2;

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            // 可选：开启更高质量（稍慢）
            g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);

            g2.drawImage(backgroundImage, x, y, drawW, drawH, this);

            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4F));
            g2.setColor(Color.WHITE);
            g2.fillRect(x, y, drawW, drawH);

        } finally {
            g2.dispose();
        }
    }
}