package com.andresblue.threatdirector.config;

/**
 * Named server configuration presets supported by Threat Director.
 *
 * <p>The first release deliberately exposes only the conservative vanilla preset. Additional
 * presets can be added without changing the JSON shape because the preset is serialized by name.
 */
public enum Preset {
    VANILLA_PLUS
}
