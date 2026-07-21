package com.andresblue.threatdirector;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ThreatDirectorMod implements ModInitializer {
    public static final String MOD_ID = "threatdirector";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Threat Director initializing for Minecraft 1.20.1");
    }
}
