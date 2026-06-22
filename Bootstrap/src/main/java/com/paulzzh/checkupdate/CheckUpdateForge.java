package com.paulzzh.checkupdate;

import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.forgespi.locating.IModLocator;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class CheckUpdateForge extends CheckUpdate implements IModLocator {
    @Override
    public List<?> scanMods() {
        return Collections.emptyList();
    }

    @Override
    public String name() {
        return "";
    }

    @Override
    public void scanFile(IModFile modFile, Consumer<Path> pathConsumer) {

    }

    @Override
    public void initArguments(Map<String, ?> arguments) {

    }

    @Override
    public boolean isValid(IModFile modFile) {
        return false;
    }
}
