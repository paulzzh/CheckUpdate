package com.paulzzh.checkupdate;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;

import com.paulzzh.checkupdate.swing.ImageBackgroundPanel;

import static com.paulzzh.checkupdate.Utils.*;

public class Main {

    private static JPanel main;

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

    public static void main(String[] args) throws FileNotFoundException {
        SwingUtilities.invokeLater(Main::initMainFrame);

        File file = new File(LOCK_FILE);
        RandomAccessFile raf = new RandomAccessFile(file, "rw");
        FileChannel channel = raf.getChannel();
        FileLock lock = acquireLockWithRetry(channel, 10, 500);;

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
}
