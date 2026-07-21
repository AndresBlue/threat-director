package com.andresblue.threatdirector.power;

public enum PowerAxis {
    OFFENSE(35_000),
    DEFENSE(35_000),
    SUSTAIN(15_000),
    MOBILITY(10_000),
    UTILITY(5_000);

    private final int cap;

    PowerAxis(int cap) {
        this.cap = cap;
    }

    public int cap() {
        return cap;
    }
}
