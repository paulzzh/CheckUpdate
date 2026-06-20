package com.paulzzh.checkupdate;

import com.google.gson.reflect.TypeToken;
import com.paulzzh.checkupdate.gson.Config;
import com.paulzzh.checkupdate.gson.HashSizeTime;
import com.paulzzh.checkupdate.gson.Info;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
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
    private final Path downloadBasePath = Paths.get(CACHE_DIR, "dl");
    private final Path infoBasePath = Paths.get(CACHE_DIR, "info");
    private final Path downloadCachePath = Paths.get(CACHE_DIR, "dl.json");

    public CacheManager(Config config, Info info, DownloadManager downloadManager, Updater.Logger logger) throws IOException {
        this.config = config;
        this.LOGGER = logger;
        this.info = info;
        this.downloadManager = downloadManager;

        flushGameCache();
        flushDownloadCache();
    }

    private void flushGameCache() throws IOException {
        Files.createDirectories(Paths.get(CACHE_DIR));
        Path cachePath = Paths.get(CACHE_DIR, "cache.json");

        if (!Files.exists(cachePath)) {
            Files.write(cachePath, "{}".getBytes(StandardCharsets.UTF_8));
        }

        try (Reader reader = new InputStreamReader(Files.newInputStream(cachePath), StandardCharsets.UTF_8)) {
            Map<String, HashSizeTime> cacheOld = GSON.fromJson(reader, new TypeToken<Map<String, HashSizeTime>>() {
            }.getType());
            info.versions.forEach((ver, files) ->
                    files.forEach((file, meta) -> {
                        if (checkPath(file)) {
                            Path path = Paths.get(file);
                            if (Files.exists(path)) {
                                HashSizeTime meta2 = makeCache(cacheOld, file);
                                gameCache.put(file, meta2);
                                gameHash.put(meta2.hash, path);
                            }
                        } else {
                            LOGGER.info("warning: " + file);
                        }
                    }));
        } catch (IOException ignored) {
        }

        Files.write(cachePath, GSON.toJson(gameCache).getBytes(StandardCharsets.UTF_8));
    }

    private void flushDownloadCache() throws IOException {
        Path cacheDir = Paths.get(CACHE_DIR);
        Files.createDirectories(cacheDir);

        if (!Files.exists(downloadCachePath)) {
            Files.write(downloadCachePath, "{}".getBytes(StandardCharsets.UTF_8));
        }

        try (Reader reader = new InputStreamReader(Files.newInputStream(downloadCachePath), StandardCharsets.UTF_8)) {
            Map<String, HashSizeTime> cacheOld = GSON.fromJson(reader, new TypeToken<Map<String, HashSizeTime>>() {
            }.getType());
            walkdir(downloadBasePath, (path) -> {
                if (Files.isRegularFile(path)) {
                    String file = path.toString().replace("\\", "/");
                    if (file.endsWith(".!part")) {
                        LOGGER.info("skip: " + file);
                    } else {
                        downloadCache.put(file, makeCache(cacheOld, file));
                    }
                }
            });
            walkdir(infoBasePath, (path) -> {
                if (Files.isRegularFile(path)) {
                    String file = path.toString().replace("\\", "/");
                    downloadCache.put(file, makeCache(cacheOld, file));
                }
            });
        } catch (IOException ignored) {
        }

        Files.write(downloadCachePath, GSON.toJson(downloadCache).getBytes(StandardCharsets.UTF_8));
    }

    private void makeDownloadCache(String hash, Path path) {
        try {
            String dlFile = path.toString().replace("\\", "/");
            BasicFileAttributes stats = Files.readAttributes(path, BasicFileAttributes.class);
            long size = stats.size();
            long time = stats.lastModifiedTime().to(TimeUnit.NANOSECONDS);
            downloadCache.put(dlFile, new HashSizeTime(hash, size, time));
            Files.write(downloadCachePath, GSON.toJson(downloadCache).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private HashSizeTime makeCache(Map<String, HashSizeTime> cache, String path) {
        try {
            BasicFileAttributes stats = Files.readAttributes(Paths.get(BASE, path), BasicFileAttributes.class);
            long size = stats.size();
            long time = stats.lastModifiedTime().to(TimeUnit.NANOSECONDS);
            if (cache.containsKey(path) && cache.get(path).size == size && cache.get(path).time == time) {
                return cache.get(path);
            } else {
                LOGGER.info("modify: " + path);
                return new HashSizeTime(getSHA256(new File(path)), size, time);
            }
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String, HashSizeTime> getGameCache() {
        return gameCache;
    }

    public Path getInfoFile(String file) {
        try {
            HashSizeTime meta = info.info.get(file);
            Path path = Paths.get(CACHE_DIR, "info", file);
            String dlFile = path.toString().replace("\\", "/");
            Files.createDirectories(path.getParent());

            if (downloadCache.containsKey(dlFile) && Files.exists(path)) {
                if (meta.hash.equals(downloadCache.get(dlFile).hash)) {
                    return path;
                }
            }
            String url = config.host + "objects/" + meta.hash;
            downloadManager.submit(new DownloadManager.DownloadTask(url, path.toFile(), meta.hash,
                    config.retry, config.connectTimeout, config.readTimeout,
                    (task, hash) -> makeDownloadCache(hash, path)));

            downloadManager.awaitAllFinished();
            return getInfoFile(file);
        } catch (IOException | InterruptedException e) {
            return null;
        }
    }

    public void getFile(String path, HashSizeTime meta) {
        getFile(path, meta, true);
    }

    public Path getFile(String file, HashSizeTime meta, Boolean dl) {
        try {
            Path path = Paths.get(CACHE_DIR, "dl", file);
            String dlFile = path.toString().replace("\\", "/");
            Files.createDirectories(path.getParent());

            if (downloadCache.containsKey(dlFile) && Files.exists(path)) {
                if (meta.hash.equals(downloadCache.get(dlFile).hash)) {
                    return path;
                } else {
                    Files.delete(path);
                    downloadCache.remove(dlFile);
                }
            }

            if (gameHash.containsKey(meta.hash) && Files.exists(gameHash.get(meta.hash))) {
                Files.copy(gameHash.get(meta.hash), path, StandardCopyOption.REPLACE_EXISTING);
                makeDownloadCache(meta.hash, path);
                return path;
            }

            if (dl) {
                String url = config.host + "objects/" + meta.hash;
                downloadManager.submit(new DownloadManager.DownloadTask(url, path.toFile(), meta.hash,
                        config.retry, config.connectTimeout, config.readTimeout,
                        (task, hash) -> makeDownloadCache(hash, path)));
            }
            return null;
        } catch (IOException | InterruptedException e) {
            return null;
        }
    }
}
