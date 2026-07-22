package com.andresblue.threatdirector.apocalypse.other;

import com.andresblue.threatdirector.apocalypse.other.OtherHostilePolicy.Family;
import net.minecraft.entity.mob.BlazeEntity;
import net.minecraft.entity.mob.DrownedEntity;
import net.minecraft.entity.mob.EndermiteEntity;
import net.minecraft.entity.mob.GhastEntity;
import net.minecraft.entity.mob.GuardianEntity;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.entity.mob.MagmaCubeEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.entity.mob.PiglinBruteEntity;
import net.minecraft.entity.mob.RavagerEntity;
import net.minecraft.entity.mob.SilverfishEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.mob.WitchEntity;
import net.minecraft.entity.raid.RaiderEntity;

final class OtherHostileFamilies {
    private OtherHostileFamilies() {
    }

    static boolean isSupported(MobEntity mob) {
        // Every vanilla Monster receives the common long-range pressure goal. Dedicated
        // families still keep their own combat module; familyOf() returns OTHER for them so no
        // second thematic ability is installed here.
        return mob instanceof Monster;
    }

    static Family familyOf(MobEntity mob) {
        if (mob instanceof RavagerEntity || mob instanceof PiglinBruteEntity) {
            return Family.BRUTE;
        }
        if (mob instanceof WitchEntity || mob instanceof RaiderEntity) {
            return Family.WITCH_ILLAGER;
        }
        if (mob instanceof BlazeEntity || mob instanceof GhastEntity || mob instanceof PhantomEntity) {
            return Family.AERIAL_FIRE;
        }
        if (mob instanceof GuardianEntity) {
            return Family.GUARDIAN;
        }
        if (mob instanceof DrownedEntity || mob instanceof HuskEntity) {
            return Family.DROWNED_HUSK;
        }
        if (mob instanceof SilverfishEntity
                || mob instanceof EndermiteEntity
                || mob instanceof SlimeEntity
                || mob instanceof MagmaCubeEntity) {
            return Family.SWARM;
        }
        return Family.OTHER;
    }
}
