package com.andresblue.threatdirector.compatibility;

/** Tri-valued answer from a claim/protection provider. */
public enum ClaimProtectionStatus {
    /** A provider positively identified protection at the position. */
    PROTECTED,
    /** Every consulted provider positively identified the position as unprotected. */
    UNPROTECTED,
    /** Protection could not be determined safely. */
    UNKNOWN;

    /** Destructive behavior is fail-closed: only an explicit unprotected result permits it. */
    public boolean permitsDestructiveMobAction() {
        return this == UNPROTECTED;
    }
}
