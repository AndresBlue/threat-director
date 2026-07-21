package com.andresblue.threatdirector.performance;

public record LoadSample(
        long serverTick,
        double meanMspt,
        double p95Mspt,
        double hostileEntityRatio,
        double activeControllerRatio,
        double planningQueueRatio
) {
    public double effectiveMspt() {
        return Math.max(finite(meanMspt), finite(p95Mspt) * .80);
    }

    public double capacityRatio() {
        return Math.max(finite(hostileEntityRatio),
                Math.max(finite(activeControllerRatio), finite(planningQueueRatio)));
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }
}
