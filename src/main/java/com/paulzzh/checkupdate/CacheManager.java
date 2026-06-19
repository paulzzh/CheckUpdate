package com.paulzzh.checkupdate;

import com.google.gson.reflect.TypeToken;
import com.paulzzh.checkupdate.gson.Config;
import com.paulzzh.checkupdate.gson.HashSizeTime;
import com.paulzzh.checkupdate.gson.Info;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static com.paulzzh.checkupdate.Utils.*;

public class CacheManager {
    private final Config config;
    private final Updater.Logger LOGGER;
    private final Info info;
    private final DownloadManager downloadManager;
    private final Map<String, HashSizeTime> gameCache = new HashMap<>();
    private final Map<String, Path> gameHash = new HashMap<>();
    private final Map<String, HashSizeTime> downloadCache = new ConcurrentHashMap<>();
    private final Path downloadBase = Paths.get(CACHE_DIR, "dl");
    private final Path downloadCacheFile = Paths.get(CACHE_DIR, "dl.json");

    public CacheManager(Config config,Info info, DownloadManager downloadManager, Updater.Logger logger) throws IOException {
        this.config = config;
        this.LOGGER = logger;
        this.info = info;
        this.downloadManager = downloadManager;

        flushGameCache();
        flushDownloadCache();
    }

    private void flushGameCache() throws IOException {
        Files.createDirectories(Paths.get(CACHE_DIR));
        Path cacheFile = Paths.get(CACHE_DIR, "cache.json");

        if (!Files.exists(cacheFile)) {
            Files.write(cacheFile, "{}".getBytes(StandardCharsets.UTF_8));
        }

        try (Reader reader = new InputStreamReader(new FileInputStream(cacheFile.toFile()), StandardCharsets.UTF_8)) {
            Map<String, HashSizeTime> cacheOld = GSON.fromJson(reader, new TypeToken<Map<String, HashSizeTime>>(){}.getType());
            info.versions.forEach((ver,files) -> {
                files.forEach((path, meta) -> {
                    if (checkPath(path)) {
                        Path file = Paths.get(path);
                        if (Files.exists(file)) {
                            HashSizeTime meta2 = makeCache(cacheOld, path);
                            gameCache.put(path, meta2);
                            gameHash.put(meta2.hash, file);
                        }
                    } else {
                        LOGGER.info("warning: "+path);
                    }
                });
            });
        } catch (IOException ignored) {
        }

        Files.write(cacheFile, GSON.toJson(gameCache).getBytes(StandardCharsets.UTF_8));
    }

    private void flushDownloadCache() throws IOException {
        Files.createDirectories(Paths.get(CACHE_DIR));

        if (!Files.exists(downloadCacheFile)) {
            Files.write(downloadCacheFile, "{}".getBytes(StandardCharsets.UTF_8));
        }

        try (Reader reader = new InputStreamReader(new FileInputStream(downloadCacheFile.toFile()), StandardCharsets.UTF_8)) {
            Map<String, HashSizeTime> cacheOld = GSON.fromJson(reader, new TypeToken<Map<String, HashSizeTime>>(){}.getType());
            walkdir(downloadBase, (file) -> {
                if (file.toFile().isFile()) {
                    String path = file.toString().replace("\\", "/");
                    downloadCache.put(path, makeCache(cacheOld, path));
                }
            });
        } catch (IOException ignored) {
        }

        Files.write(downloadCacheFile, GSON.toJson(downloadCache).getBytes(StandardCharsets.UTF_8));
    }

    private void makeDownloadCache(String hash, Path path) {
        try {
            BasicFileAttributes stats = Files.readAttributes(path, BasicFileAttributes.class);
            long size = stats.size();
            long time = stats.lastModifiedTime().to(TimeUnit.NANOSECONDS);
            downloadCache.put(path.toString(), new HashSizeTime(hash, size, time));
            Files.write(downloadCacheFile, GSON.toJson(downloadCache).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private HashSizeTime makeCache(Map<String, HashSizeTime> cache, String path) {
        try {
            BasicFileAttributes stats = Files.readAttributes(Paths.get(BASE,path), BasicFileAttributes.class);
            long size = stats.size();
            long time = stats.lastModifiedTime().to(TimeUnit.NANOSECONDS);
            if (cache.containsKey(path) && cache.get(path).size == size && cache.get(path).time == time) {
                return cache.get(path);
            } else {
                LOGGER.info("modify: "+path);
                return new HashSizeTime(getSHA256(new File(path)), size, time);
            }
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String, HashSizeTime> getGameCache() {
        return gameCache;
    }

    public Path getFile(String path, HashSizeTime meta) {
        return getFile(path,meta,true);
    }

    public Path getFile(String path, HashSizeTime meta, Boolean dl) {
        try {
            Path file = Paths.get(CACHE_DIR, "dl", path);
            String dlpath = file.toString();
            Files.createDirectories(file.getParent());

            if (downloadCache.containsKey(dlpath) && Files.exists(file)) {
                if (meta.hash.equals(downloadCache.get(dlpath).hash)){
                    return file;
                } else {
                    Files.delete(file);
                    downloadCache.remove(dlpath);
                }
            }

            if (gameHash.containsKey(meta.hash) && Files.exists(gameHash.get(meta.hash))) {
                Files.copy(gameHash.get(meta.hash), file, StandardCopyOption.REPLACE_EXISTING);
                makeDownloadCache(meta.hash, file);
                return file;
            }

            if (dl) {
                String url = config.host + "objects/" + meta.hash;
                downloadManager.submit(new DownloadManager.DownloadTask(url, file.toFile(), meta.hash,
                        3, 10_000, 30_000,
                        (task, hash) -> makeDownloadCache(hash, file)));
            }
            return null;
        } catch (IOException|InterruptedException e) {
            return null;
        }
    }
}
