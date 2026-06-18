package com.paulzzh.checkupdate;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.concurrent.*;

import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import java.io.*;
import java.util.concurrent.*;

import static com.paulzzh.checkupdate.Utils.bytesToHex;

public class DownloadManager implements Closeable {

    public interface DownloadCallback {
        void onSuccess(DownloadTask task);
        void onFailure(DownloadTask task, Exception e);
        void onProgress(DownloadTask task, long bytesRead, long totalBytes, double percent);
    }

    public static class DownloadTask {
        private final String url;
        private final File targetFile;
        private final String hash;
        private final int maxRetries;
        private final int connectTimeoutMs;
        private final int readTimeoutMs;

        public DownloadTask(String url,
                            File targetFile,
                            String hash,
                            int maxRetries,
                            int connectTimeoutMs,
                            int readTimeoutMs) {
            this.url = Objects.requireNonNull(url);
            this.targetFile = Objects.requireNonNull(targetFile);
            this.hash = hash;
            this.maxRetries = Math.max(0, maxRetries);
            this.connectTimeoutMs = connectTimeoutMs;
            this.readTimeoutMs = readTimeoutMs;
        }

        public DownloadTask(String url, File targetFile, String hash) {
            this(url, targetFile, hash, 3, 10_000, 30_000);
        }

        public String getUrl() { return url; }
        public File getTargetFile() { return targetFile; }
        public int getMaxRetries() { return maxRetries; }
        public int getConnectTimeoutMs() { return connectTimeoutMs; }
        public int getReadTimeoutMs() { return readTimeoutMs; }
    }

    private final Semaphore semaphore;
    private final ExecutorService executor;
    private final DownloadCallback callback;

    private final Object finishLock = new Object();
    private int runningTasks = 0;

    public DownloadManager(int maxConcurrent, DownloadCallback callback) {
        this.semaphore = new Semaphore(maxConcurrent);
        this.executor = Executors.newFixedThreadPool(maxConcurrent);
        this.callback = callback;
    }

    public void submit(final DownloadTask task) throws InterruptedException {
        semaphore.acquire();

        synchronized (finishLock) {
            runningTasks++;
        }

        try {
            executor.execute(() -> {
                Exception lastError = null;
                boolean success = false;

                try {
                    for (int i = 0; i <= task.getMaxRetries(); i++) {
                        try {
                            downloadOnce(task);
                            success = true;
                            break;
                        } catch (Exception e) {
                            lastError = e;
                            if (i < task.getMaxRetries()) {
                                Thread.sleep(500L * (i + 1));
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    lastError = e;
                } finally {
                    semaphore.release();
                    synchronized (finishLock) {
                        runningTasks--;
                        if (runningTasks == 0) {
                            finishLock.notifyAll();
                        }
                    }
                }

                if (getCallback() != null) {
                    if (success) {
                        getCallback().onSuccess(task);
                    } else {
                        getCallback().onFailure(task, lastError);
                    }
                }
            });
        } catch (RuntimeException e) {
            semaphore.release();
            synchronized (finishLock) {
                runningTasks--;
                if (runningTasks == 0) {
                    finishLock.notifyAll();
                }
            }
            throw e;
        }
    }

    private void downloadOnce(DownloadTask task) throws IOException, NoSuchAlgorithmException {
        URL url = new URL(task.getUrl());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(task.getConnectTimeoutMs());
        conn.setReadTimeout(task.getReadTimeoutMs());

        File target = task.getTargetFile();
        File tmp = new File(target.getAbsolutePath() + ".part");

        long total = conn.getContentLengthLong(); // 关键点
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (InputStream in = new BufferedInputStream(conn.getInputStream());
             OutputStream out = new BufferedOutputStream(new FileOutputStream(tmp))) {

            byte[] buf = new byte[8192];
            int len;
            long read = 0;

            long lastCallbackTime = 0;

            // 开始0%
            if (getCallback() != null) {
                getCallback().onProgress(task, read, total, 0.0);
            }

            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
                digest.update(buf, 0, len);
                read += len;

                if (getCallback() != null) {
                    long now = System.currentTimeMillis();

                    // 限流：500ms一次
                    if (now - lastCallbackTime >= 500) {
                        lastCallbackTime = now;

                        double percent = (total > 0) ? (read * 1.0 / total) : -1;
                        getCallback().onProgress(task, read, total, percent);
                    }
                }
            }

            if (!bytesToHex(digest.digest()).equals(task.hash)) {
                throw new RuntimeException();
            }

            // 最后强制回调一次100%
            if (getCallback() != null) {
                double percent = (total > 0) ? 1.0 : -1;
                getCallback().onProgress(task, read, total, percent);
            }
        }

        Files.move(tmp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private DownloadCallback getCallback() {
        return callback;
    }

    public void awaitAllFinished() throws InterruptedException {
        synchronized (finishLock) {
            while (runningTasks > 0) {
                TimeUnit.SECONDS.timedWait(finishLock, 1);
            }
        }
    }

    public void shutdown() {
        executor.shutdown();
    }

    @Override
    public void close() {
        shutdown();
    }
}