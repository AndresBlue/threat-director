package com.andresblue.threatdirector.power;

import java.util.List;

public interface PowerEvaluator {
    String id();

    default int priority() {
        return 1_000;
    }

    List<PowerContribution> evaluate(PlayerPowerSnapshot snapshot);
}
