package com.paulzzh.checkupdate;

import com.paulzzh.checkupdate.gson.Result;
import com.paulzzh.checkupdate.swing.LogPanel;
import com.paulzzh.checkupdate.swing.MainWindow;

import javax.annotation.Nonnull;
import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.paulzzh.checkupdate.Utils.LOCK_FILE;

public class Main {

    private final static ConcurrentLinkedQueue<String> logCache = new ConcurrentLinkedQueue<>();
    private final static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    public static void main(String[] args) throws IOException, InterruptedException {
        SwingUtilities.invokeLater(MainWindow::new);

        Updater updater = getUpdater();
        Result result = updater.checkUpdate();
        MainWindow.INSTANCE.showMainUI(
                new ImageIcon(updater.getIcon().toString()),
                updater.getConfig().name,
                updater.getConfig().version + " --> " + result.version,
                new ImageIcon(updater.getBackground().toString()).getImage());

        Thread.sleep(1000);
        MainWindow.INSTANCE.getFoot().updateTask(new DownloadManager.DownloadTask("a", new File("CheckUpdate.config"), "bbb", 3, 0, 0, null), "失败 ", -1);

        File file = new File(LOCK_FILE);
        RandomAccessFile raf = new RandomAccessFile(file, "rw");
        FileChannel channel = raf.getChannel();
        FileLock lock = acquireLockWithRetry(channel, 10, 500);

        while (lock == null) {
            Object[] options = {"重试", "退出"};
            int choice = JOptionPane.showOptionDialog(MainWindow.INSTANCE, "强制退出Minecraft失败!\\n请手动结束游戏进程!",
                    "检查更新 - 错误", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE, null, options, options[0]);
            if (choice == JOptionPane.NO_OPTION) {
                System.exit(0);
            }
            lock = acquireLockWithRetry(channel, 10, 500);
        }

    }

    @Nonnull
    private static Updater getUpdater() throws IOException, InterruptedException {
        DownloadManager downloadManager = new DownloadManager(8, new DownloadManager.ManagerCallback() {
            @Override
            public void onSuccess(DownloadManager.DownloadTask task) {
                //info("下载完成 "+task.getTargetFile().getName());
                MainWindow.INSTANCE.getFoot().removeTask(task); // 完成后直接清理
            }

            @Override
            public void onFailure(DownloadManager.DownloadTask task, Exception e) {
                info("下载失败 " + task.getTargetFile().getName() + " " + e.getMessage());
                MainWindow.INSTANCE.getFoot().updateTask(task, "失败 " + e.getMessage(), -1);
            }

            @Override
            public void onProgress(DownloadManager.DownloadTask task, long bytesRead, long totalBytes, double percent) {
                String status;
                if (totalBytes > 0) {
                    status = String.format("%.2f%%", percent * 100);
                } else {
                    status = bytesRead + " bytes";
                }
                info("正在下载 " + task.getTargetFile().getName() + " " + status);
                MainWindow.INSTANCE.getFoot().updateTask(task, "正在下载 " + status, percent);
            }
        });

        Updater updater = new Updater(Main::info, downloadManager);
        return updater;
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

    public static void info(Object o) {
        System.out.println(o);
        String log = "[" + LocalDateTime.now().format(formatter) + "] " + o.toString();
        if (MainWindow.INSTANCE != null && MainWindow.INSTANCE.getLog() != null) {
            LogPanel logger = MainWindow.INSTANCE.getLog();
            String element;
            while ((element = logCache.poll()) != null) {
                logger.appendLog(element);
            }
            logger.appendLog(log);
        } else {
            logCache.add(log);
        }
    }
}
