package com.paulzzh.checkupdate;

import com.google.gson.Gson;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
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
    public final static String MOD_ID = "checkupdate";
    public final static String MOD_PACKAGE = "com.paulzzh.checkupdate.";
    public final static String LOCK_FILE = "CheckUpdate.lock";
    public final static String JAR = "CheckUpdate.jar";
    public final static String CONF = "CheckUpdate.config";
    public final static String CACHE_DIR = "CheckUpdateCache";
    public final static String BACKUP_DIR = "CheckUpdateBackup";
    public final static String PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAnTC/qb22DOTl4U0nAUEwl+8P8U84mlizZ7SHA9ldG3ShRoQEyc2cU0MbxIKZtIPL+sHrfCt+iypDFIHzhORw8Uo/U973yx4/jhgJmBjxLIUCcG8qjC431bMNEeZ/Pp5czHqYp1vQcU+eFfeaLZN4qN1GVtK4kz3GLFfAhyNsh8vZAl5LGV7B3h37ReGSIA8mXrcJCfAI6hdP8kEtViIbWG5d7cSn9Y05h6cHOrAYFw3Zozt4fsI9w7KsTecMfuHVEO+wmeZuN3yj32oWRygJR/xb0x+x7bFN9rgyMOGSr45eFtmjFEa5KSvdMDu2aetlIkYkW8c5BIgFSmxUODz93QIDAQAB";
    public final static Gson GSON = new Gson();
    public final static String BASE = Paths.get("").toAbsolutePath().normalize().toString();

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

    public static String getSHA256(File file) throws IOException, NoSuchAlgorithmException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(bytes);
        return bytesToHex(hash);
    }

    public static String getJava() {
        String javaHome = System.getProperty("java.home");
        String binaryName = System.getProperty("os.name").toLowerCase().contains("win") ? "javaw.exe" : "java";
        File javaExec = new File(new File(javaHome, "bin"), binaryName);
        return javaExec.getAbsolutePath();
    }

    public static boolean checkPath(String file) {
        return Paths.get(file).toAbsolutePath().normalize().startsWith(BASE);
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
