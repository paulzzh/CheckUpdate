package com.paulzzh.checkupdate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
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
    private final static RandomAccessFile raf;
    private final static FileLock lock;

    static {
        try {
            File file = LOCK_FILE.toFile();
            raf = new RandomAccessFile(file, "rw");
            lock = raf.getChannel().tryLock();
            if (lock == null || !lock.isValid()) {
                LOGGER.fatal("Cannot get lock!");
                cpw.mods.fml.common.com.paulzzh.checkupdate.SafeRuntimeExit.exitRuntime(0);
            }
            ensureJar();
            if (checkUpdate(LOGGER::info)) {
                LOGGER.fatal("Please update modpack!");
                launchGUI();
                cpw.mods.fml.common.com.paulzzh.checkupdate.SafeRuntimeExit.exitRuntime(0);
            }

        } catch (Exception e) {
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
