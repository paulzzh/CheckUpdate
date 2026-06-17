package com.paulzzh.checkupdate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;

import static com.paulzzh.checkupdate.CheckUpdate.MOD_ID;
import static com.paulzzh.checkupdate.CheckUpdate.MOD_PACKAGE;
import static com.paulzzh.checkupdate.Utils.getPID;
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
    private static final String JAR = "CheckUpdate.jar";

    static {
        try {
            if (true) {
                File checkUpdateJar = new File(JAR);
                InputStream inputStream = CheckUpdate.class.getProtectionDomain().getCodeSource().getLocation().openStream();
                if (inputStream != null) {
                    Files.copy(inputStream, checkUpdateJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }

                File outputFile = new File("CheckUpdate.log");
                String[] cmd = {checkUpdateJar.getAbsolutePath(), "--gui", "--pid", getPID()};
                ProcessBuilder pb2 = new ProcessBuilder(cmd);
                pb2.redirectOutput(outputFile);
                pb2.redirectError(outputFile);
                pb2.directory(Paths.get("").toAbsolutePath().toFile());
                pb2.start();
                LOGGER.fatal("Please update modpack");
                exitRuntime(0);
                throw new RuntimeException();
            }
        } catch (IOException e) {
            throw new RuntimeException();
        }
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
