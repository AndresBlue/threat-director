package com.andresblue.threatdirector.awareness;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Optional;
import java.util.UUID;

/** Small per-world ring buffer. All access occurs on the server thread. */
final class AwarenessSoundBuffer {
    static final int CAPACITY = 64;

    private final Deque<AwarenessSoundPulse> pulses = new ArrayDeque<>(CAPACITY);

    void record(AwarenessPolicy.SoundRules rules, BlockPos position,
                UUID sourcePlayerId, long tick) {
        purge(tick);
        AwarenessSoundPulse last = pulses.peekLast();
        if (last != null
                && last.tick() == tick
                && last.rules().type() == rules.type()
                && last.position().getSquaredDistance(position) <= 4.0) {
            return;
        }
        while (pulses.size() >= CAPACITY) {
            pulses.removeFirst();
        }
        pulses.addLast(new AwarenessSoundPulse(
                rules,
                position.toImmutable(),
                sourcePlayerId,
                tick
        ));
    }

    Optional<AwarenessSoundPulse> latestHeard(AwarenessProfile profile, BlockPos listener,
                                               long afterTick, long now) {
        purge(now);
        Iterator<AwarenessSoundPulse> iterator = pulses.descendingIterator();
        while (iterator.hasNext()) {
            AwarenessSoundPulse pulse = iterator.next();
            if (pulse.tick() <= afterTick) {
                break;
            }
            double distanceSquared = listener.getSquaredDistance(pulse.position());
            if (AwarenessPolicy.canHear(
                    profile,
                    pulse.rules(),
                    distanceSquared,
                    now - pulse.tick()
            )) {
                return Optional.of(pulse);
            }
        }
        return Optional.empty();
    }

    void clear() {
        pulses.clear();
    }

    private void purge(long now) {
        while (!pulses.isEmpty()
                && now - pulses.peekFirst().tick() > pulses.peekFirst().rules().lifetimeTicks()) {
            pulses.removeFirst();
        }
    }
}
