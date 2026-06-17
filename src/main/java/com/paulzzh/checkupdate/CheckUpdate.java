package com.paulzzh.checkupdate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import static com.paulzzh.checkupdate.CheckUpdate.MOD_ID;
import static com.paulzzh.checkupdate.CheckUpdate.MOD_PACKAGE;
import static cpw.mods.fml.common.com.paulzzh.checkupdate.SafeRuntimeExit.exitRuntime;

//lower 1.7.10
@cpw.mods.fml.relauncher.IFMLLoadingPlugin.Name(MOD_ID)
@cpw.mods.fml.relauncher.IFMLLoadingPlugin.TransformerExclusions(MOD_PACKAGE)
// upper 1.8
@net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.Name(MOD_ID)
@net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.TransformerExclusions(MOD_PACKAGE)
public class CheckUpdate implements
        //lower 1.7.10
        cpw.mods.fml.relauncher.IFMLLoadingPlugin,
        //upper 1.8
        net.minecraftforge.fml.relauncher.IFMLLoadingPlugin {
    public static final String MOD_ID = "checkupdate";
    public static final String MOD_PACKAGE = "com.paulzzh.checkupdate.";
    private static final Logger LOGGER = LogManager.getLogger(CheckUpdate.class.getSimpleName());
    private static final String EXEC = "CheckUpdate.exe";
    private static final String TRUST_HASH = "709523286d9ce8cd87a2ec9fa0f94c21f61afcad38cfb924258127afce4f4e5b";

    static {
        try {
            String pid = getPID();
            File checkUpdateExec = new File(EXEC);
            Boolean fileValid = false;
            while (!fileValid) {
                if (checkUpdateExec.isFile() && TRUST_HASH.equals(getSHA256(checkUpdateExec))) {
                    fileValid = true;
                } else {
                    LOGGER.warn("CheckUpdate.exe invalid! Extracting file...");
                    Thread.sleep(1000);
                    InputStream inputStream = CheckUpdate.class.getResourceAsStream("/" + EXEC);
                    if (inputStream != null) {
                        Files.copy(inputStream, checkUpdateExec.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            String[] cmd = {checkUpdateExec.getAbsolutePath(), "--pid", pid};
            ProcessBuilder pb = new ProcessBuilder(cmd);
            Map<String, String> env = pb.environment();
            env.put("PYTHONIOENCODING", "utf-8");
            pb.directory(Paths.get("").toAbsolutePath().toFile());
            Process p = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), Charset.forName("UTF-8")));
            String line;
            while ( (line = reader.readLine()) != null) {
                LOGGER.info(line);
            }
            p.waitFor();
            int errorCode = p.exitValue();
            LOGGER.warn("CheckUpdate.exe exit with code " + errorCode);
            if (errorCode != 0) {
                File outputFile = new File("CheckUpdate.log");
                String[] cmd2 = {checkUpdateExec.getAbsolutePath(), "--gui", "--pid", pid};
                ProcessBuilder pb2 = new ProcessBuilder(cmd2);
                pb2.redirectOutput(outputFile);
                pb2.redirectError(outputFile);
                pb2.directory(Paths.get("").toAbsolutePath().toFile());
                pb2.start();
                LOGGER.fatal("Please update modpack");
                exitRuntime(0);
                throw new RuntimeException();
            }
        } catch (IOException | InterruptedException | NoSuchAlgorithmException e) {
            throw new RuntimeException();
        }
    }

    private static String bytesToHex(byte[] hash) {
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

    private static String getSHA256(File file) throws IOException, NoSuchAlgorithmException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(bytes);
        return bytesToHex(hash);
    }

    private static String getPID() {
        return ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {

    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
