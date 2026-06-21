package com.paulzzh.checkupdate.gui;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.Comparator;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class Utils {
    public final static Path HOME = getHome();
    public final static Path CONF = Paths.get("CheckUpdate.config");
    public final static Path LOCK_FILE = Paths.get("CheckUpdate.lock");

    public final static Path CACHE_DIR = Paths.get("CheckUpdateCache");
    public final static Path BACKUP_DIR = Paths.get("CheckUpdateBackup");

    public final static Gson GSON = new Gson();

    public static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public static String getSHA256(URL resourceUrl) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream is = resourceUrl.openStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        byte[] hash = digest.digest();
        return bytesToHex(hash);
    }

    public static boolean checkPath(String file) {
        return Paths.get(file).toAbsolutePath().normalize().startsWith(HOME);
    }

    public static void walkdir(Path path, Consumer<Path> consumer) {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(consumer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void deletefile(Path path) {
        try {
            Files.delete(path);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String formatBytes(long bytes) {
        if (-1000 < bytes && bytes < 1000) {
            return bytes + "B";
        }
        CharacterIterator ci = new StringCharacterIterator("KMGTPE");
        while (bytes <= -999_950 || bytes >= 999_950) {
            bytes /= 1000;
            ci.next();
        }
        return String.format("%.1f%cB", bytes / 1000.0, ci.current());
    }

    public static String formatSeconds(long totalSeconds) {
        if (totalSeconds > 43200) {
            return ">12h";
        }

        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;

        return String.format("%d:%02d", minutes, seconds);
    }

    public static void runAsync(Runnable runnable) {
        new Thread(runnable).start();
    }

    public static String getStackTraceAsString(Throwable throwable) {
        try (StringWriter sw = new StringWriter();
             PrintWriter pw = new PrintWriter(sw)) {

            throwable.printStackTrace(pw);
            return sw.toString();
        } catch (Exception e) {
            return "Failed to extract stack trace";
        }
    }

    public static Path getHome() {
        Path home = Paths.get("").toAbsolutePath().normalize();
        if (home.getFileName().toString().equals("mods")) {
            return home.getParent();
        }
        return home;
    }

    public static class VersionComparator implements Comparator<String> {

        @Override
        public int compare(String v1, String v2) {
            if (v1 == null && v2 == null) return 0;
            if (v1 == null) return -1;
            if (v2 == null) return 1;

            // Split versions into numeric parts
            String[] parts1 = v1.split("\\.");
            String[] parts2 = v2.split("\\.");

            int maxLength = Math.max(parts1.length, parts2.length);
            for (int i = 0; i < maxLength; i++) {
                // Treat missing sub-version numbers as 0 (e.g., 1.2 vs 1.2.3)
                int num1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
                int num2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;

                if (num1 != num2) {
                    return Integer.compare(num1, num2);
                }
            }
            return 0;
        }
    }
}
