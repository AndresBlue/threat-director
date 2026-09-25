package com.andresblue.threatdirector.awareness;

import net.minecraft.util.math.BlockPos;

import java.util.UUID;

record AwarenessSoundPulse(AwarenessPolicy.SoundRules rules, BlockPos position,
                           UUID sourcePlayerId, long tick) {
}
