package com.andresblue.threatdirector.tactical;

public record TacticalFrame(double anchorX, int anchorY, double anchorZ, Vec2 forward, Vec2 right) {
    public static TacticalFrame from(BlockPoint player, Vec2 horizontalFacing, Vec2 meanVelocity) {
        Vec2 facing = horizontalFacing == null ? Vec2.NORTH : horizontalFacing;
        Vec2 velocity = meanVelocity == null ? new Vec2(0.0, 0.0) : meanVelocity;
        Vec2 forward = facing.length() >= .20
                ? facing.normalizedOr(Vec2.NORTH)
                : velocity.normalizedOr(Vec2.NORTH);
        Vec2 lead = velocity.scale(20.0).clampMagnitude(6.0);
        Vec2 right = new Vec2(-forward.z(), forward.x());
        return new TacticalFrame(player.x() + lead.x(), player.y(), player.z() + lead.z(), forward, right);
    }

    public Vec2 direction(TacticalSector sector) {
        return switch (sector) {
            case FRONT, UPPER, LOWER, COVER -> forward;
            case REAR, RESERVE -> forward.scale(-1.0);
            case LEFT -> right.scale(-1.0);
            case RIGHT -> right;
            case ESCAPE_PRIMARY, ESCAPE_SECONDARY -> forward.scale(-1.0);
        };
    }
}
