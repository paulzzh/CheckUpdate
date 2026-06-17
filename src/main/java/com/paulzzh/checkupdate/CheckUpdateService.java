package com.paulzzh.checkupdate;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;
import java.util.Set;

//Forge/NeoForged 1.13+
public class CheckUpdateService extends CheckUpdate implements ITransformationService {
    @Nonnull
    @Override
    public String name() {
        return "CheckUpdateService";
    }

    @Override
    public void initialize(IEnvironment environment) {

    }

    @Override
    public void beginScanning(IEnvironment environment) {

    }

    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) {

    }

    @Nonnull
    @Override
    public List<ITransformer> transformers() {
        return Collections.emptyList();
    }
}