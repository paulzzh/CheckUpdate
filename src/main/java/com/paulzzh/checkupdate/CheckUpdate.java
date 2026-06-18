package com.paulzzh.checkupdate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;

import static com.paulzzh.checkupdate.Utils.*;

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
    private final static Logger LOGGER = LogManager.getLogger(CheckUpdate.class.getSimpleName());

    static {
        try {
            File file = new File(LOCK_FILE);
            RandomAccessFile raf = new RandomAccessFile(file, "rw");
            FileLock lock = raf.getChannel().tryLock();
            if (lock == null || !lock.isValid()) {
                LOGGER.fatal("Cannot get lock!");
                cpw.mods.fml.common.com.paulzzh.checkupdate.SafeRuntimeExit.exitRuntime(0);
            }
            File checkUpdateJar = new File(JAR);
            String jarPath = CheckUpdate.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
            LOGGER.info("jar: "+jarPath);
            Files.copy(new File(jarPath).toPath(), checkUpdateJar.toPath(), StandardCopyOption.REPLACE_EXISTING);

            DownloadManager downloadManager = new DownloadManager(8, null);
            Updater updater = new Updater(LOGGER::info, downloadManager);
            if (updater.checkUpdate().restart) {
                File outputFile = new File("CheckUpdate.log");
                String[] cmd = {getJava(), "-jar", checkUpdateJar.getAbsolutePath()};
                ProcessBuilder pb2 = new ProcessBuilder(cmd);
                pb2.redirectOutput(outputFile);
                pb2.redirectError(outputFile);
                pb2.directory(new File(BASE));
                pb2.start();

                LOGGER.fatal("Please update modpack!");
                cpw.mods.fml.common.com.paulzzh.checkupdate.SafeRuntimeExit.exitRuntime(0);
            } else {
                updater = null;
                downloadManager.shutdown();
                downloadManager = null;
                if (lock != null) {
                    lock.release();
                    raf.close();
                }
            }
        } catch (IOException | InterruptedException | URISyntaxException e) {
            throw new RuntimeException(e);
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
