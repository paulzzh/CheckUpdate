package com.paulzzh.checkupdate;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.function.Consumer;

public class Utils {
    public final static String MOD_ID = "checkupdate";
    public final static String MOD_PACKAGE = "com.paulzzh.checkupdate.";

    public final static Path HOME = getHome();
    public final static Path JAR = HOME.resolve("CheckUpdate.jar");
    public final static Path LOG = HOME.resolve("CheckUpdate.log");
    public final static Path LOCK_FILE = HOME.resolve("CheckUpdate.lock");

    public static boolean checkUpdate(Consumer<Object> info) throws Exception {
        URLClassLoader loader = new URLClassLoader(new URL[]{JAR.toUri().toURL()}, CheckUpdate.class.getClassLoader());
        Class<?> loadedClass = Class.forName("com.paulzzh.checkupdate.gui.Updater", true, loader);

        Constructor<?> constructor = Arrays.stream(loadedClass.getConstructors()).findFirst().get();
        return (boolean) loadedClass.getMethod("checkRestart").invoke(constructor.newInstance(info, null));
    }

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

    public static void ensureJar() throws IOException, NoSuchAlgorithmException {
        URL res = Utils.class.getResource("/META-INF/jar-jar/CheckUpdate.jar");
        if (Files.exists(JAR)) {
            if (getSHA256(JAR.toUri().toURL()).equals(getSHA256(res))) {
                return;
            }
        }
        Files.copy(res.openStream(), JAR, StandardCopyOption.REPLACE_EXISTING);
    }

    public static void launchGUI() throws IOException {
        File outputFile = LOG.toFile();
        String[] cmd = {getJava(), "-jar", JAR.toString()};
        ProcessBuilder pb2 = new ProcessBuilder(cmd);
        pb2.redirectOutput(outputFile);
        pb2.redirectError(outputFile);
        pb2.directory(HOME.toFile());
        pb2.start();
    }

    public static String getJava() {
        String javaHome = System.getProperty("java.home");
        String binaryName = System.getProperty("os.name").toLowerCase().contains("win") ? "javaw.exe" : "java";
        File javaExec = new File(new File(javaHome, "bin"), binaryName);
        return javaExec.getAbsolutePath();
    }

    public static Path getHome() {
        Path home = Paths.get("").toAbsolutePath().normalize();
        if (home.getFileName().toString().equals("mods")) {
            return home.getParent();
        }
        return home;
    }
}
