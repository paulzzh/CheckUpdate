package com.paulzzh.checkupdate;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.management.ManagementFactory;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Utils {
    public static final String MOD_ID = "checkupdate";
    public static final String MOD_PACKAGE = "com.paulzzh.checkupdate.";
    public static final String LOCK_FILE = "checkupdate.lock";
    public static final String JAR = "checkupdate.jar";

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
}
