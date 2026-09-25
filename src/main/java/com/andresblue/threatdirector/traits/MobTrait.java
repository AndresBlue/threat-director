package com.andresblue.threatdirector.traits;

/**
 * Persistent systemic variants for hostile mobs. These are deliberately
 * independent from {@code com.andresblue.threatdirector.trait.MobTrait}, which
 * describes the tactical capabilities of a mob type.
 */
public enum MobTrait {
    COWARD(18),
    BERSERKER(18),
    LEADER(7),
    REGENERATIVE(12),
    SWIFT(22),
    RESILIENT(16);

    private final int selectionWeight;

    MobTrait(int selectionWeight) {
        this.selectionWeight = selectionWeight;
    }

    public int selectionWeight() {
        return selectionWeight;
    }

    public boolean isCompatibleWith(MobTrait other) {
        if (other == null || other == this) {
            return other != this;
        }
        // These two instincts would continuously fight over the same low-health state.
        return !(this == COWARD && other == BERSERKER)
                && !(this == BERSERKER && other == COWARD);
    }
}
