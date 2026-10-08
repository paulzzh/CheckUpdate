package com.paulzzh.checkupdate;

import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;

public class CheckUpdateNeo extends CheckUpdate implements IModFileCandidateLocator {
    static {
        LOGGER.info("CheckUpdateNeo - NeoforgeSPI");
    }

    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {

    }
}
