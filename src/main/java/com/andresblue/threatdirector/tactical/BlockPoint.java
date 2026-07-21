package com.andresblue.threatdirector.tactical;

public record BlockPoint(int x, int y, int z) implements Comparable<BlockPoint> {
    public double horizontalDistance(BlockPoint other) {
        return StrictMath.hypot(x - other.x, z - other.z);
    }

    public int manhattanDistance(BlockPoint other) {
        return Math.abs(x - other.x) + Math.abs(y - other.y) + Math.abs(z - other.z);
    }

    @Override
    public int compareTo(BlockPoint other) {
        int result = Integer.compare(x, other.x);
        if (result != 0) return result;
        result = Integer.compare(y, other.y);
        return result != 0 ? result : Integer.compare(z, other.z);
    }
}
