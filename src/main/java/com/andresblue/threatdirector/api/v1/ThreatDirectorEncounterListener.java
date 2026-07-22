package com.andresblue.threatdirector.api.v1;

@FunctionalInterface
public interface ThreatDirectorEncounterListener {
    void onEncounterEvent(EncounterEvent event);
}
