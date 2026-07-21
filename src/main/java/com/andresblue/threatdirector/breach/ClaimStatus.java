package com.andresblue.threatdirector.breach;

public enum ClaimStatus {
    NO_SYSTEM,
    UNCLAIMED,
    /** Compatibility synonym used by runtime adapters. */
    UNPROTECTED,
    PROTECTED,
    UNKNOWN
}
