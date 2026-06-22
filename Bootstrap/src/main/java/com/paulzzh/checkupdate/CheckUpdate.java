package com.paulzzh.checkupdate;

import cpw.mods.fml.common.com.paulzzh.checkupdate.SafeRuntimeExit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.security.NoSuchAlgorithmException;

import static com.paulzzh.checkupdate.Utils.*;

public class CheckUpdate {

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
                SafeRuntimeExit.exitRuntime(0);
            }
            ensureJar();
            if (checkUpdate(LOGGER::info)) {
                LOGGER.fatal("Please update modpack!");
                launchGUI();
                SafeRuntimeExit.exitRuntime(0);
            }
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
