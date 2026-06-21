package com.paulzzh.checkupdate.gui;

import com.paulzzh.checkupdate.gui.gson.Config;
import com.paulzzh.checkupdate.gui.gson.HashSizeTime;
import com.paulzzh.checkupdate.gui.gson.Info;
import com.paulzzh.checkupdate.gui.gson.Result;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.paulzzh.checkupdate.gui.Utils.*;

public class Updater {
    private final DownloadManager downloadManager;
    private final CacheManager cacheManager;
    private final Consumer<Object> LOGGER;
    private final Config config;
    private final Info info;
    private final int MAJOR;
    private final int MINOR;
    private final Path icon;
    private final Path background;

    public Updater(Consumer<Object> logger, DownloadManager.ManagerCallback callback) throws IOException {
        this.LOGGER = logger;
        this.config = readConfig();
        this.downloadManager = new DownloadManager(config.thread, callback);
        this.info = readInfo();
        this.cacheManager = new CacheManager(config, info, downloadManager, logger);

        String[] parts = config.version.split("\\.");
        this.MAJOR = Integer.parseInt(parts[0]);
        this.MINOR = Integer.parseInt(parts[1]);

        this.icon = cacheManager.getInfoFile("icon.png");
        this.background = cacheManager.getInfoFile("background.png");
    }

    private Config readConfig() {
        try (Reader reader = new InputStreamReader(Files.newInputStream(CONF), StandardCharsets.UTF_8)) {
            Config c = GSON.fromJson(reader, Config.class);
            if (c.thread <= 0) {
                c.thread = 8;
            }
            if (c.retry <= 0) {
                c.retry = 5;
            }
            if (c.connectTimeout < 1_000) {
                c.connectTimeout = 5_000;
            }
            if (c.readTimeout < 1_000) {
                c.readTimeout = 30_000;
            }
            return c;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Info readInfo() throws IOException {

        String url = config.host + URLEncoder.encode(config.name, StandardCharsets.UTF_8.name()) + "/info";
        LOGGER.accept("baseurl: " + url);
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(30_000);
        conn.setInstanceFollowRedirects(true);

        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code + " for " + url);
        }
        byte[] sign = Base64.getDecoder().decode(conn.getHeaderField("X-Signature"));

        try (InputStream in = conn.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            byte[] keyBytes = Base64.getDecoder().decode(config.publicKey);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(kf.generatePublic(spec));

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                signature.update(buffer, 0, bytesRead);
            }

            signature.verify(sign);
            return GSON.fromJson(new InputStreamReader(new ByteArrayInputStream(out.toByteArray()), StandardCharsets.UTF_8), Info.class);

        } catch (InvalidKeySpecException | NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            throw new RuntimeException(e);
        }

    }

    public void update(Result result, Runnable runnable) {
        try {
            if (result.restart) {
                if (result.major) {
                    updateMajor(result.version, result.filelist);
                } else {
                    update(result.version, result.filelist);
                }
            }
            runnable.run();
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void updateMajor(String version, Map<String, HashSizeTime> needUpdate) throws InterruptedException, IOException {
        String now = String.valueOf(Instant.now().getEpochSecond());
        Path tempDirPath = CACHE_DIR.resolve(now);
        walkdir(tempDirPath, Utils::deletefile);
        download(needUpdate);
        needUpdate.forEach((file, meta) -> {
            try {
                Path dlPath = cacheManager.getFile(file, meta, false);
                Path tempPath = CACHE_DIR.resolve(now).resolve(file);
                Files.createDirectories(tempPath.getParent());
                LOGGER.accept("安装文件: " + tempPath);
                Files.move(dlPath, tempPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        try (Stream<Path> stream = Files.list(tempDirPath)) {
            stream.forEach((tempPath) -> {
                try {
                    String dirFile = tempPath.getFileName().toString();
                    Path path = Paths.get(dirFile);
                    if (Files.exists(path)) {
                        Path backupPath = BACKUP_DIR.resolve(now).resolve(dirFile);
                        Files.createDirectories(backupPath.getParent());
                        LOGGER.accept("备份文件: " + backupPath);
                        Files.move(path, backupPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                    LOGGER.accept("移动文件: " + path);
                    Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        config.version = version;
        Files.write(CONF, GSON.toJson(config).getBytes(StandardCharsets.UTF_8));

        walkdir(CACHE_DIR.resolve("dl"), Utils::deletefile);
        walkdir(tempDirPath, Utils::deletefile);
    }

    private void update(String version, Map<String, HashSizeTime> needUpdate) throws InterruptedException, IOException {
        String now = String.valueOf(Instant.now().getEpochSecond());
        download(needUpdate);
        needUpdate.forEach((file, meta) -> {
            try {
                Path path = Paths.get(file);
                if (Files.exists(path)) {
                    Path backupPath = BACKUP_DIR.resolve(now).resolve(file);
                    Files.createDirectories(backupPath.getParent());
                    LOGGER.accept("备份文件: " + backupPath);
                    Files.move(path, backupPath, StandardCopyOption.REPLACE_EXISTING);
                }
                if (meta.size > 0) {
                    Path dlPath = cacheManager.getFile(file, meta, false);
                    if (dlPath == null) {
                        throw new RuntimeException("file not found");
                    }
                    LOGGER.accept("安装文件: " + dlPath);
                    Files.move(dlPath, path, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        config.version = version;
        Files.write(CONF, GSON.toJson(config).getBytes(StandardCharsets.UTF_8));

        walkdir(CACHE_DIR.resolve("dl"), Utils::deletefile);
    }

    public void download(Map<String, HashSizeTime> needUpdate) throws InterruptedException {
        needUpdate.entrySet().stream().filter((e) -> e.getValue().size > 0)
                .forEach((e) -> cacheManager.getFile(e.getKey(), e.getValue()));
        downloadManager.awaitAllFinished();
        needUpdate.entrySet().stream().filter((e) -> e.getValue().size > 0)
                .forEach((e) -> cacheManager.getFile(e.getKey(), e.getValue(), false));
    }

    public void setZero() throws IOException {
        config.version = "0.0";
        Files.write(CONF, GSON.toJson(config).getBytes(StandardCharsets.UTF_8));
    }

    public boolean checkRestart() throws IOException, InterruptedException {
        return checkUpdate().restart;
    }

    public Result checkUpdate() throws InterruptedException, IOException {
        List<String> versions = info.versions.keySet().stream().sorted(new Utils.VersionComparator()).collect(Collectors.toList());
        LOGGER.accept("ver: " + versions);
        String latest = versions.get(versions.size() - 1);

        if (config.version.equals(latest)) {
            LOGGER.accept("无更新。");
            return new Result(false, false, config.version, new HashMap<>());
        }

        String[] parts = latest.split("\\.");
        int majorL = Integer.parseInt(parts[0]);
        int minorL = Integer.parseInt(parts[1]);

        if (majorL == MAJOR && minorL != MINOR) {
            VersionComparator comp = new VersionComparator();
            Map<String, HashSizeTime> mayNeedUpdate = new HashMap<>();
            Map<String, HashSizeTime> needUpdate = new HashMap<>();
            AtomicBoolean modUpdate = new AtomicBoolean(false);
            versions.forEach((v) -> {
                if (comp.compare(config.version, v) < 0) {
                    mayNeedUpdate.putAll(info.versions.get(v));
                }
            });
            mayNeedUpdate.forEach((file, meta) -> {
                if (checkPath(file)) {
                    Path path = Paths.get(file);
                    boolean exist = Files.exists(path);
                    if ((!exist && meta.size != 0) || (exist && !meta.hash.equals(cacheManager.getGameCache().get(file).hash))) {
                        needUpdate.put(file, meta);
                        if ("mods".equals(path.getName(0).toString())) {
                            modUpdate.set(true);
                        }
                    }
                } else {
                    LOGGER.accept("warning: " + file);
                }
            });
            if (modUpdate.get()) {
                LOGGER.accept("检测到小版本更新");
                LOGGER.accept("mod列表改变,无法热更新,需要重启");
                return new Result(false, true, latest, needUpdate);
            } else {
                LOGGER.accept("检测到小版本更新");
                update(latest, needUpdate);
                LOGGER.accept("热更新完毕");
                return new Result(false, false, latest, new HashMap<>());
            }
        }
        LOGGER.accept("检测到大版本更新");
        LOGGER.accept("需要重置整合包");
        return new Result(true, true, majorL + ".0", info.versions.get(majorL + ".0"));
    }

    public Config getConfig() {
        return config;
    }

    public Path getIcon() {
        return icon;
    }

    public Path getBackground() {
        return background;
    }
}
