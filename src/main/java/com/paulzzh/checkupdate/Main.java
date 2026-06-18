package com.paulzzh.checkupdate;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.paulzzh.checkupdate.swing.ImageBackgroundPanel;

import static com.paulzzh.checkupdate.Utils.*;

public class Main {

    private static JPanel main;
    private static DownloadManager downloadManager;
    private static Updater updater;
    private static final Map<DownloadManager.DownloadTask, TaskRow> taskRowMap = new ConcurrentHashMap<>();
    private static final TaskTableModel tableModel = new TaskTableModel();

    private static void initMainFrame() {
        JFrame frame = new JFrame("检查更新 - 加载中");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(true);
        frame.setSize(800, 600);
        frame.setMinimumSize(frame.getSize());
        frame.setIconImage(new ImageIcon(CheckUpdate.class.getResource("/assets/checkupdate/icon.png")).getImage());

        Image image = new ImageIcon("background.png").getImage();
        main = new ImageBackgroundPanel(image);
        main.setLayout(new BorderLayout());
        frame.setContentPane(main);

        JLabel label = new JLabel("少女祈祷中...", SwingConstants.CENTER);
        main.add(label);

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void main(String[] args) throws IOException, InterruptedException {

        downloadManager = new DownloadManager(8, new DownloadManager.DownloadCallback() {
            @Override
            public void onSuccess(DownloadManager.DownloadTask task) {
                removeTask(task); // 完成后直接清理
            }

            @Override
            public void onFailure(DownloadManager.DownloadTask task, Exception e) {
                updateTask(task, "下载失败: " + e.getMessage(), -1);
            }

            @Override
            public void onProgress(DownloadManager.DownloadTask task, long bytesRead, long totalBytes, double percent) {
                String status;
                if (totalBytes > 0) {
                    status = String.format("正在下载 %.2f%%", percent * 100);
                } else {
                    status = "正在下载 " + bytesRead + " bytes";
                }
                updateTask(task, status, percent);
            }
        });

        updater = new Updater(System.out::println, downloadManager);
        updater.checkUpdate();

        SwingUtilities.invokeLater(Main::initMainFrame);

        File file = new File(LOCK_FILE);
        RandomAccessFile raf = new RandomAccessFile(file, "rw");
        FileChannel channel = raf.getChannel();
        FileLock lock = acquireLockWithRetry(channel, 10, 500);

        while (lock == null) {
            Object[] options = {"重试", "退出"};
            int choice = JOptionPane.showOptionDialog(main, "强制退出Minecraft失败!\\n请手动结束游戏进程!",
                    "检查更新 - 错误", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE, null, options, options[0]);
            if (choice == JOptionPane.NO_OPTION) {
                System.exit(0);
            }
            lock = acquireLockWithRetry(channel, 10, 500);
        }

    }

    private static FileLock acquireLockWithRetry(FileChannel channel, int maxRetries, long sleepMillis) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                // tryLock() is non-blocking; returns null if locked by another process
                FileLock lock = channel.tryLock();
                if (lock != null) {
                    return lock;
                }
            } catch (OverlappingFileLockException e) {
                // Thrown if this JVM already holds a lock on the same file region
                System.out.println("Lock already held by another thread in this JVM.");
            } catch (IOException e) {
                System.out.println("System-level locking conflict or OS interference encountered.");
            }

            System.out.printf("Attempt %d/%d failed. Retrying in %d ms...%n", attempt, maxRetries, sleepMillis);

            try {
                Thread.sleep(sleepMillis);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt(); // Restore interrupted status
                System.err.println("Lock acquisition retry loop was interrupted.");
                break;
            }
        }
        return null;
    }

    private static void updateTask(DownloadManager.DownloadTask task, String status, double percent) {
        TaskRow row = taskRowMap.get(task);
        if (row == null) {
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

    private static void removeTask(DownloadManager.DownloadTask task) {
        TaskRow row = taskRowMap.remove(task);
        if (row == null) {
            return;
        }

        SwingUtilities.invokeLater(() -> tableModel.removeTask(row));
    }

    // ===== 任务行对象 =====
    private static class TaskRow {
        final DownloadManager.DownloadTask task;

        volatile String status = "等待中";
        volatile double percent = 0.0;
        volatile boolean finished = false;
        volatile Exception error = null;

        TaskRow(DownloadManager.DownloadTask task) {
            this.task = Objects.requireNonNull(task);
        }
    }

    // ===== 表格模型 =====
    private static class TaskTableModel extends AbstractTableModel {
        private final List<TaskRow> rows = new ArrayList<>();

        private final String[] columns = {
                "文件名", "状态", "进度"
        };

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
                    return row.task.getTargetFile();
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
