package com.paulzzh.checkupdate.swing;

import com.paulzzh.checkupdate.DownloadManager;

import javax.annotation.Nonnull;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class DownloadTaskPanel extends JPanel {
    private static final Color PANEL_BG = new Color(255, 255, 255, 0);  // 外层：透明
    private static final Color CELL_BG = new Color(255, 255, 255, 102);  // 单元格：40% 白底
    private static final Color TEXT = Color.BLACK;

    private final Map<DownloadManager.DownloadTask, TaskRow> taskRowMap = new ConcurrentHashMap<>();
    private final TaskTableModel tableModel = new TaskTableModel();
    private final JTable table = new JTable(tableModel) {
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setComposite(AlphaComposite.SrcOver);
            g2.setColor(new Color(255, 255, 255, 102)); // 40% 白底
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();

            super.paintComponent(g);
        }
    };

    public DownloadTaskPanel() {
        setOpaque(false);
        setBackground(PANEL_BG);
        setLayout(new BorderLayout());

        table.setFillsViewportHeight(false); // 关键：不要让空白区也跟着表格背景走
        table.setRowHeight(26);
        table.setFocusable(false);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setShowGrid(true);
        table.setGridColor(new Color(0, 0, 0, 35));
        table.setIntercellSpacing(new Dimension(1, 1));
        table.setOpaque(false);
        table.setBackground(new Color(0, 0, 0, 0));
        table.setForeground(TEXT);

        // 单元格渲染：只让内容行半透明
        TransparentCellRenderer cellRenderer = new TransparentCellRenderer();
        for (int i = 0; i < table.getColumnModel().getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(cellRenderer);
        }

        // 自定义表头，避免默认实心样式
        JTableHeader header = getJTableHeader();
        table.setTableHeader(header);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setOpaque(false);
        scrollPane.setBackground(PANEL_BG);

        scrollPane.getViewport().setOpaque(false);
        scrollPane.getViewport().setBackground(PANEL_BG);

        add(scrollPane, BorderLayout.CENTER);
    }

    @Nonnull
    private JTableHeader getJTableHeader() {
        JTableHeader header = new JTableHeader(table.getColumnModel());
        header.setReorderingAllowed(false);
        header.setOpaque(false);
        header.setBackground(new Color(0, 0, 0, 0));
        header.setForeground(TEXT);
        header.setDefaultRenderer(new HeaderRenderer());
        return header;
    }

    public void addTask(DownloadManager.DownloadTask task) {
        TaskRow row = new TaskRow(task);
        taskRowMap.put(task, row);

        SwingUtilities.invokeLater(() -> {
            if (taskRowMap.containsKey(task)) {
                tableModel.addTask(row);
            }
        });
    }

    public void updateTask(DownloadManager.DownloadTask task, String status, double percent) {
        TaskRow row = taskRowMap.get(task);
        if (row == null) {
            addTask(task);
            return;
        }

        SwingUtilities.invokeLater(() -> {
            if (!taskRowMap.containsKey(task)) {
                return;
            }
            row.status = status;
            row.percent = percent;
            tableModel.fireTaskUpdated(row);
        });
    }

    public void finishTask(DownloadManager.DownloadTask task) {
        TaskRow row = taskRowMap.get(task);
        if (row != null) {
            row.finished = true;
            updateTask(task, "完成", 1.0);
        }
    }

    public void removeTask(DownloadManager.DownloadTask task) {
        TaskRow row = taskRowMap.remove(task);
        if (row == null) {
            return;
        }

        SwingUtilities.invokeLater(() -> tableModel.removeTask(row));
    }

    public void clearFinishedTasks() {
        for (TaskRow row : new ArrayList<>(tableModel.rows)) {
            if (row.finished) {
                removeTask(row.task);
            }
        }
    }

    private static class TransparentCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {

            JLabel c = (JLabel) super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);

            c.setOpaque(true);
            c.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
            c.setForeground(TEXT);

            if (isSelected) {
                c.setBackground(new Color(0x0D6EFD));
                c.setForeground(Color.WHITE);
            } else {
                c.setBackground(CELL_BG);
            }

            return c;
        }
    }

    private static class HeaderRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {

            JLabel c = (JLabel) super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);

            c.setOpaque(false);
            c.setHorizontalAlignment(SwingConstants.LEFT);
            c.setForeground(TEXT);
            c.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            return c;
        }
    }

    private static class TaskRow {
        final DownloadManager.DownloadTask task;
        volatile String status = "等待";
        volatile double percent = 0.0;
        volatile boolean finished = false;
        volatile Exception error = null;

        TaskRow(DownloadManager.DownloadTask task) {
            this.task = Objects.requireNonNull(task);
        }
    }

    private static class TaskTableModel extends AbstractTableModel {
        private final List<TaskRow> rows = new ArrayList<>();
        private final String[] columns = {"文件名", "状态", "进度"};

        public void addTask(TaskRow row) {
            int index = rows.size();
            rows.add(row);
            fireTableRowsInserted(index, index);
        }

        public void removeTask(TaskRow row) {
            int index = rows.indexOf(row);
            if (index >= 0) {
                rows.remove(index);
                fireTableRowsDeleted(index, index);
            }
        }

        public void fireTaskUpdated(TaskRow row) {
            int index = rows.indexOf(row);
            if (index >= 0) {
                fireTableRowsUpdated(index, index);
            }
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            TaskRow row = rows.get(rowIndex);
            switch (columnIndex) {
                case 0:
                    return row.task.getTargetFile().getName();
                case 1:
                    return row.status;
                case 2:
                    return row.percent >= 0 ? String.format("%.2f%%", row.percent * 100) : "未知";
                default:
                    return "";
            }
        }
    }
}