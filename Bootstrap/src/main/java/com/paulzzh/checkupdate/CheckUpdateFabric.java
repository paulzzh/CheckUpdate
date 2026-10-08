package com.paulzzh.checkupdate;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

//Fabric
public class CheckUpdateFabric extends CheckUpdate implements PreLaunchEntrypoint {
    static {
        LOGGER.info("CheckUpdateFabric");
    }

    @Override
    public void onPreLaunch() {
    }
}
