package com.andresblue.threatdirector.config;

import java.util.List;

/** Raised when a configuration cannot safely be used by the server runtime. */
public final class ConfigValidationException extends IllegalArgumentException {
    private final List<String> violations;

    public ConfigValidationException(List<String> violations) {
        super("Invalid Threat Director configuration: " + String.join("; ", violations));
        this.violations = List.copyOf(violations);
    }

    public List<String> violations() {
        return violations;
    }
}
