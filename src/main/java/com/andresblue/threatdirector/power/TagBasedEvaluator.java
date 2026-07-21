package com.andresblue.threatdirector.power;

import java.util.List;

/** Generic semantic-tag contribution point; item ids are deliberately not part of the contract. */
public final class TagBasedEvaluator implements PowerEvaluator {
    @Override
    public String id() {
        return "generic:tags";
    }

    @Override
    public int priority() {
        return 150;
    }

    @Override
    public List<PowerContribution> evaluate(PlayerPowerSnapshot snapshot) {
        double utility = Double.isFinite(snapshot.utilityPotential()) ? snapshot.utilityPotential() : 0.0;
        return List.of(new PowerContribution(
                PowerAxis.UTILITY,
                "tags.utility",
                (int) Math.round(5_000.0 * Math.max(0.0, Math.min(1.0, utility))),
                1.0,
                "Semantic item/effect tags"
        ));
    }
}
