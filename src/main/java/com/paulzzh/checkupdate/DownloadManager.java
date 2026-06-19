package com.paulzzh.checkupdate;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static com.paulzzh.checkupdate.Utils.bytesToHex;

public class DownloadManager implements Closeable {

    private final Semaphore semaphore;
    private final ExecutorService executor;
    private final ManagerCallback callback;
    private final Object finishLock = new Object();
    private int runningTasks = 0;
    public DownloadManager(int maxConcurrent, ManagerCallback callback) {
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
                String hash = null;
                Exception lastError = null;
                boolean success = false;

                try {
                    for (int i = 0; i <= task.getMaxRetries(); i++) {
                        try {
                            hash = downloadOnce(task);
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
                }

                try {
                    // 先回调
                    if (success && task.callback != null) {
                        task.callback.onSuccess(task, hash);
                    }

                    if (this.callback != null) {
                        if (success) {
                            this.callback.onSuccess(task);
                        } else {
                            this.callback.onFailure(task, lastError);
                        }
                    }
                } finally {
                    // 再释放控制资源
                    semaphore.release();
                    synchronized (finishLock) {
                        runningTasks--;
                        if (runningTasks == 0) {
                            finishLock.notifyAll();
                        }
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

    private String downloadOnce(DownloadTask task) throws IOException, NoSuchAlgorithmException {
        URL url = new URL(task.getUrl());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(task.getConnectTimeoutMs());
        conn.setReadTimeout(task.getReadTimeoutMs());

        File target = task.getTargetFile();
        File tmp = new File(target.getAbsolutePath() + ".part");

        long total = conn.getContentLengthLong(); // 关键点
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String hash;

        try (InputStream in = new BufferedInputStream(conn.getInputStream());
             OutputStream out = new BufferedOutputStream(new FileOutputStream(tmp))) {

            byte[] buf = new byte[8192];
            int len;
            long read = 0;

            long lastCallbackTime = 0;

            // 开始0%
            if (this.callback != null) {
                this.callback.onProgress(task, read, total, 0.0);
            }

            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
                digest.update(buf, 0, len);
                read += len;

                if (this.callback != null) {
                    long now = System.currentTimeMillis();

                    // 限流：1000ms一次
                    if (now - lastCallbackTime >= 1000) {
                        lastCallbackTime = now;

                        double percent = (total > 0) ? (read * 1.0 / total) : -1;
                        this.callback.onProgress(task, read, total, percent);
                    }
                }
            }

            hash = bytesToHex(digest.digest());
            if (!hash.equals(task.hash)) {
                throw new RuntimeException();
            }

            // 最后强制回调一次100%
            if (this.callback != null) {
                double percent = (total > 0) ? 1.0 : -1;
                this.callback.onProgress(task, read, total, percent);
            }
        }

        Files.move(tmp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return hash;
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

    public interface ManagerCallback {
        void onSuccess(DownloadTask task);

        void onFailure(DownloadTask task, Exception e);

        void onProgress(DownloadTask task, long bytesRead, long totalBytes, double percent);
    }

    public interface DownloadCallback {
        void onSuccess(DownloadTask task, String hash);
    }

    public static class DownloadTask {
        private final String url;
        private final File targetFile;
        private final String hash;
        private final int maxRetries;
        private final int connectTimeoutMs;
        private final int readTimeoutMs;
        private final DownloadCallback callback;

        public DownloadTask(String url,
                            File targetFile,
                            String hash,
                            int maxRetries,
                            int connectTimeoutMs,
                            int readTimeoutMs,
                            DownloadCallback callback) {
            this.url = Objects.requireNonNull(url);
            this.targetFile = Objects.requireNonNull(targetFile);
            this.hash = hash;
            this.maxRetries = Math.max(0, maxRetries);
            this.connectTimeoutMs = connectTimeoutMs;
            this.readTimeoutMs = readTimeoutMs;
            this.callback = callback;
        }

        public String getUrl() {
            return url;
        }

        public File getTargetFile() {
            return targetFile;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }
    }
}