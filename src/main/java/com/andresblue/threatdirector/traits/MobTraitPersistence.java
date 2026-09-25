package com.andresblue.threatdirector.traits;

import java.util.Set;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;

/** Assigns traits once and stores the result in vanilla's persistent entity Tags NBT list. */
public final class MobTraitPersistence {
    private MobTraitPersistence() {
    }

    public static Set<MobTrait> assignIfAbsent(MobEntity mob, ServerWorld world) {
        Set<String> tags = mob.getCommandTags();
        Set<MobTrait> existing = TraitTagCodec.decode(tags);
        if (tags.contains(TraitTagCodec.ROLLED_TAG)) {
            return existing;
        }

        Set<MobTrait> selected = existing.isEmpty()
                ? TraitSelectionPolicy.select(
                        TraitDifficulty.fromDifficultyId(world.getDifficulty().getId()),
                        seedFor(mob, world))
                : existing;
        for (MobTrait trait : selected) {
            mob.addCommandTag(TraitTagCodec.encode(trait));
        }
        // The marker also persists a valid "no traits" roll, preventing reload rerolls.
        mob.addCommandTag(TraitTagCodec.ROLLED_TAG);
        return Set.copyOf(selected);
    }

    public static Set<MobTrait> read(MobEntity mob) {
        return TraitTagCodec.decode(mob.getCommandTags());
    }

    private static long seedFor(MobEntity mob, ServerWorld world) {
        return world.getSeed()
                ^ Long.rotateLeft(mob.getUuid().getMostSignificantBits(), 17)
                ^ mob.getUuid().getLeastSignificantBits();
    }
}
