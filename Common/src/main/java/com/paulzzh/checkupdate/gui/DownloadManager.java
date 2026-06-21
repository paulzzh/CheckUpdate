package com.paulzzh.checkupdate.gui;

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

import static com.paulzzh.checkupdate.gui.Utils.bytesToHex;
import static com.paulzzh.checkupdate.gui.Utils.getSHA256;

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

    private static long parseTotalFromContentRange(String contentRange) {
        if (contentRange == null) return -1L;
        // 例：bytes 100-199/1000
        int slash = contentRange.lastIndexOf('/');
        if (slash < 0 || slash == contentRange.length() - 1) return -1L;
        String totalStr = contentRange.substring(slash + 1).trim();
        if ("*".equals(totalStr)) return -1L;
        try {
            return Long.parseLong(totalStr);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    public void submit(final DownloadTask task) throws InterruptedException {
        semaphore.acquire();

        synchronized (finishLock) {
            runningTasks++;
        }

        try {
            executor.execute(() -> {
                if (this.callback != null) {
                    this.callback.onAdd(task);
                }

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
                            this.callback.onSuccess(task, hash);
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
        conn.setRequestProperty("Accept-Encoding", "identity");

        File target = task.getTargetFile();
        File tmp = new File(target.getAbsolutePath() + ".!part");

        long existing = tmp.exists() ? tmp.length() : 0L;

        // 如果已有临时文件，断点续传
        if (existing > 0) {
            conn.setRequestProperty("Range", "bytes=" + existing + "-");
        }

        int code = conn.getResponseCode();
        // 先处理服务器拒绝 Range 的情况
        if (existing > 0 && code == HttpURLConnection.HTTP_OK) {
            // 服务器忽略了 Range，说明不支持续传或资源已变化，直接重下
            existing = 0L;
            try (FileOutputStream ignored = new FileOutputStream(tmp, false)) {
                // truncate tmp
            }
        }

        // 416：请求范围不合法。常见于“本地 part 已经等于完整文件大小”
        if (code == 416 && existing > 0) {
            long remoteTotal = parseTotalFromContentRange(conn.getHeaderField("Content-Range"));
            if (remoteTotal > 0 && remoteTotal == existing) {
                // 临时文件可能已经完整，直接校验 hash
                String localHash = getSHA256(tmp.toURI().toURL());
                if (!localHash.equalsIgnoreCase(task.hash)) {
                    throw new IOException("断点文件已存在，但校验失败: " + tmp.getAbsolutePath());
                }
                Files.move(tmp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return localHash;
            }

            // 其他情况：删掉坏的 part，重新下载
            if (tmp.exists() && !tmp.delete()) {
                throw new IOException("无法删除损坏的临时文件: " + tmp.getAbsolutePath());
            }
            existing = 0L;

            conn.disconnect();
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(task.getConnectTimeoutMs());
            conn.setReadTimeout(task.getReadTimeoutMs());
            conn.setRequestProperty("Accept-Encoding", "identity");
        }

        long total;
        if (existing > 0) {
            total = parseTotalFromContentRange(conn.getHeaderField("Content-Range"));
        } else {
            total = conn.getContentLengthLong();
        }

        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        // 如果是续传，先把已有 part 的内容喂给 digest
        if (existing > 0) {
            try (InputStream seed = new BufferedInputStream(Files.newInputStream(tmp.toPath()))) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = seed.read(buf)) != -1) {
                    digest.update(buf, 0, len);
                }
            }
        }

        long read = existing;
        long lastCallbackTime = 0L;
        long totalTimeMiles = 0L;
        long totalRead = 0L;
        int wait = 500;

        try (InputStream in = new BufferedInputStream(conn.getInputStream());
             OutputStream out = new BufferedOutputStream(new FileOutputStream(tmp, existing > 0))) {

            byte[] buf = new byte[8192];
            int len;

            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
                digest.update(buf, 0, len);
                read += len;
                totalRead += len;

                if (this.callback != null) {
                    long now = System.currentTimeMillis();
                    if (now - lastCallbackTime >= wait) {
                        totalTimeMiles += wait;
                        lastCallbackTime = now;
                        double percent = (total > 0) ? (read * 1.0 / total) : -1;
                        long eta = (total > 0 && totalRead > 0) ? ((total - read) / (totalRead / totalTimeMiles) / 1000) : 3601;
                        this.callback.onProgress(task, read, total, percent, eta);
                    }
                }
            }
        }

        String hash = bytesToHex(digest.digest());
        if (!hash.equalsIgnoreCase(task.hash)) {
            // 校验失败，删掉 part，避免下次继续续到错误内容上
            if (tmp.exists() && !tmp.delete()) {
                throw new IOException("下载校验失败，且无法删除临时文件: " + tmp.getAbsolutePath());
            }
            throw new IOException("下载校验失败");
        }

        if (this.callback != null) {
            double percent = (total > 0) ? 1.0 : -1;
            this.callback.onProgress(task, read, total, percent, 0);
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
        void onAdd(DownloadTask task);

        void onSuccess(DownloadTask task, String hash);

        void onFailure(DownloadTask task, Exception e);

        void onProgress(DownloadTask task, long bytesRead, long totalBytes, double percent, long eta);
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