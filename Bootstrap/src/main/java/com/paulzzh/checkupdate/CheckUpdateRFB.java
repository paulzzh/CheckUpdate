package com.paulzzh.checkupdate;

import com.gtnewhorizons.retrofuturabootstrap.api.RfbPlugin;

public class CheckUpdateRFB extends CheckUpdate implements RfbPlugin {
    static {
        LOGGER.info("CheckUpdateRFB - RetroFuturaBootstrap");
    }
}
