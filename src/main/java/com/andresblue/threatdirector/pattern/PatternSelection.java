package com.andresblue.threatdirector.pattern;

import java.util.List;
import java.util.Optional;

public record PatternSelection(Optional<PatternEvaluation> selected, List<PatternEvaluation> evaluations) {
    public PatternSelection {
        selected = selected == null ? Optional.empty() : selected;
        evaluations = List.copyOf(evaluations);
    }
}
