package com.paulzzh.checkupdate.gui.swing;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import java.awt.*;
import java.util.ArrayDeque;

public class LogPanel extends JPanel {
    private static final Color PANEL_BG = new Color(255, 255, 255, 102);

    // 最多保留的日志行数
    private static final int MAX_LOG_LINES = 1000;

    // 每次 EDT 最多处理多少条日志，避免一次刷新时间过长
    private static final int MAX_BATCH_LINES = 100;

    private final JTextArea textArea = new JTextArea();

    // appendLog() 可以被任意线程调用
    private final ArrayDeque<String> pendingLogs = new ArrayDeque<>();

    // 由 pendingLogs 自身保护
    private boolean flushScheduled;

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
        g2.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
        );
        g2.setColor(PANEL_BG);
        g2.fillRect(0, 0, getWidth() - 20, getHeight() - 20);
        g2.dispose();
        super.paintComponent(g);
    }

    public void appendLog(String text) {
        synchronized (pendingLogs) {
            pendingLogs.add(text);

            if (!flushScheduled) {
                flushScheduled = true;
                SwingUtilities.invokeLater(this::flushLogs);
            }
        }
    }

    private void flushLogs() {
        StringBuilder batch = new StringBuilder();

        synchronized (pendingLogs) {
            int count = 0;

            while (count < MAX_BATCH_LINES && !pendingLogs.isEmpty()) {
                batch.append(pendingLogs.poll()).append('\n');
                count++;
            }
        }

        if (batch.length() > 0) {
            try {
                Document document = textArea.getDocument();

                // 一次性插入，而不是一条一条修改 Document
                document.insertString(
                        document.getLength(),
                        batch.toString(),
                        null
                );

                // 删除旧日志
                removeOldLogs(document);

                // 保持原来的行为：始终滚动到底部
                textArea.setCaretPosition(document.getLength());

            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        }

        synchronized (pendingLogs) {
            if (!pendingLogs.isEmpty()) {
                // 还有日志，继续让下一个 EDT 任务处理
                SwingUtilities.invokeLater(this::flushLogs);
            } else {
                flushScheduled = false;

                // 防止在刚检查完队列后发生竞态
                if (!pendingLogs.isEmpty()) {
                    flushScheduled = true;
                    SwingUtilities.invokeLater(this::flushLogs);
                }
            }
        }
    }

    private void removeOldLogs(Document document) throws BadLocationException {
        Element root = document.getDefaultRootElement();

        int lineCount = root.getElementCount();

        // JTextArea 末尾换行后会多一个空行 Element
        int maxElements = MAX_LOG_LINES + 1;

        if (lineCount <= maxElements) {
            return;
        }

        int linesToRemove = lineCount - maxElements;

        Element lastRemovedLine = root.getElement(linesToRemove - 1);
        int removeEnd = lastRemovedLine.getEndOffset();

        document.remove(0, removeEnd);
    }
}