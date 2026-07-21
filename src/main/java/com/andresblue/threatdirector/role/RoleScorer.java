package com.andresblue.threatdirector.role;

import com.andresblue.threatdirector.trait.MobProfile;
import com.andresblue.threatdirector.trait.MobTrait;

public final class RoleScorer {
    private RoleScorer() {}

    /** Returns -1 when the mob is ineligible or its required route is unavailable. */
    public static int score(MobProfile mob, TacticalRole role, RoleContext context) {
        if (!mob.alive()) return -1;
        double h = unit(mob.healthFraction());
        double a = unit(mob.armor() / 20.0);
        double s = unit((mob.movementSpeed() - .20) / .15);
        double path = pathFor(role, context);
        if (path < 0.0) return -1;

        double value = switch (role) {
            case VANGUARD -> mob.has(MobTrait.MELEE) && !mob.has(MobTrait.EXPLOSIVE)
                    ? 250 + 180 * yes(mob, MobTrait.TANKY) + 220 * h + 120 * a + 100 * path
                    - 120 * yes(mob, MobTrait.FAST) - 80 * yes(mob, MobTrait.CLIMBING)
                    : -1;
            case FLANKER -> closeThreat(mob) && mobileFlanker(mob, s)
                    ? 180 + 220 * yes(mob, MobTrait.FAST) + 150 * yes(mob, MobTrait.CLIMBING)
                    + 100 * yes(mob, MobTrait.STEALTHY) + 80 * yes(mob, MobTrait.EXPLOSIVE)
                    + 130 * s + 150 * path - 100 * yes(mob, MobTrait.TANKY)
                    : -1;
            case HARASSER -> mob.has(MobTrait.RANGED)
                    ? 400 + 200 + 120 * unit(mob.rangeNorm()) + 120 * unit(context.coverQuality())
                    + 80 * h + 60 * path
                    : -1;
            case INTERCEPTOR -> interceptorCandidate(mob, s)
                    ? 180 + 180 * yes(mob, MobTrait.FAST) + 120 * yes(mob, MobTrait.CLIMBING)
                    + 160 * yes(mob, MobTrait.EXPLOSIVE) + 140 * s + 160 * path + 60 * h
                    : -1;
            default -> -1;
        };
        return value < 0 ? -1 : (int) Math.round(Math.min(1_000.0, value));
    }

    private static boolean closeThreat(MobProfile mob) {
        return mob.has(MobTrait.MELEE) || mob.has(MobTrait.EXPLOSIVE);
    }

    private static boolean mobileFlanker(MobProfile mob, double speedNorm) {
        return mob.has(MobTrait.FAST) || mob.has(MobTrait.CLIMBING)
                || mob.has(MobTrait.STEALTHY) || speedNorm >= .33;
    }

    private static boolean interceptorCandidate(MobProfile mob, double speedNorm) {
        return mob.has(MobTrait.FAST) || mob.has(MobTrait.CLIMBING)
                || mob.has(MobTrait.EXPLOSIVE) || speedNorm >= .33;
    }

    private static double pathFor(TacticalRole role, RoleContext context) {
        double value = switch (role) {
            case VANGUARD -> context.vanguardPathQuality();
            case FLANKER -> context.flankerPathQuality();
            case HARASSER -> context.harasserPathQuality();
            case INTERCEPTOR -> context.interceptorPathQuality();
            default -> -1.0;
        };
        if (!Double.isFinite(value) || value < 0.0) return -1.0;
        return unit(value);
    }

    private static double yes(MobProfile mob, MobTrait trait) {
        return mob.has(trait) ? 1.0 : 0.0;
    }

    private static double unit(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }
}
