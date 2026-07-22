package com.andresblue.threatdirector.difficulty;

/** Pure tool-to-block relationship used by zombie breach/pathing integrations. */
public enum ZombieToolRole {
    NONE,
    SHOVEL,
    AXE,
    SWORD,
    PICKAXE;

    public boolean prefers(BlockFamily family) {
        return switch (this) {
            case SHOVEL -> family == BlockFamily.SHOVEL_MINEABLE;
            case AXE -> family == BlockFamily.AXE_MINEABLE;
            case SWORD -> family == BlockFamily.SWORD_EFFICIENT;
            case PICKAXE -> family == BlockFamily.PICKAXE_MINEABLE;
            case NONE -> false;
        };
    }

    public enum BlockFamily {
        SHOVEL_MINEABLE,
        AXE_MINEABLE,
        SWORD_EFFICIENT,
        PICKAXE_MINEABLE,
        OTHER
    }
}
