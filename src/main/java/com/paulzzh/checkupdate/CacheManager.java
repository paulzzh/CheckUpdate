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
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.paulzzh.checkupdate.Utils.*;

public class CacheManager {
    private final Config config;
    private final Updater.Logger LOGGER;
    private final Info info;
    private final DownloadManager downloadManager;
    private final Map<String, HashSizeTime> gameCache = new HashMap<>();
    private Map<String, Path> gameHash = new HashMap<>();
    private Map<String, Path> downloadCache = new HashMap<>();

    public CacheManager(Config config,Info info, DownloadManager downloadManager, Updater.Logger logger) throws IOException {
        this.config = config;
        this.LOGGER = logger;
        this.info = info;
        this.downloadManager = downloadManager;

        flushCache();
    }

    private void flushCache() throws IOException {
        Files.createDirectories(Paths.get(CACHE_DIR));
        Path cache = Paths.get(CACHE_DIR, "cache.json");

        if (!Files.exists(cache)) {
            Files.write(cache, "{}".getBytes(StandardCharsets.UTF_8));
        }

        try (Reader reader = new InputStreamReader(new FileInputStream(cache.toFile()), StandardCharsets.UTF_8)) {
            Map<String, HashSizeTime> gameCacheOld = GSON.fromJson(reader, new TypeToken<Map<String, HashSizeTime>>(){}.getType());
            info.versions.forEach((ver,files) -> {
                files.forEach((path, meta) -> {
                    if (checkPath(path)) {
                        Path file = Paths.get(path);
                        if (Files.exists(file)) {
                            HashSizeTime meta2 = makeCache(gameCacheOld, path);
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

        Files.write(cache, GSON.toJson(gameCache).getBytes(StandardCharsets.UTF_8));
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
        try {
            Path file = Paths.get(CACHE_DIR, "dl", path);
            Files.createDirectories(file.getParent());

            if (downloadCache.containsKey(path) && Files.exists(downloadCache.get(path))) {
                return downloadCache.get(path);
            }

            if (gameHash.containsKey(meta.hash) && Files.exists(gameHash.get(meta.hash))) {
                Files.copy(gameHash.get(meta.hash), file, StandardCopyOption.REPLACE_EXISTING);
                downloadCache.put(path, file);
                return downloadCache.get(path);
            }

            String url = config.host + "objects/" + meta.hash;
            downloadManager.submit(new DownloadManager.DownloadTask(url, file.toFile(), meta.hash));
            downloadCache.put(path, file);
            return null;
        } catch (IOException|InterruptedException e) {
            return null;
        }
    }
}
