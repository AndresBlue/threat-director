package com.andresblue.threatdirector.mixin;

import com.andresblue.threatdirector.difficulty.MinecraftDifficultyProfiles;
import com.andresblue.threatdirector.difficulty.SpiderDifficultyAbilities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobEntity.class)
public abstract class MobEntityDifficultyMixin {
    @Unique
    private long threatdirector$nextCobwebTick;

    @Inject(method = "tryAttack", at = @At("RETURN"))
    private void threatdirector$placeSpiderCobweb(Entity target,
                                                  CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValue()
                || !((Object) this instanceof SpiderEntity spider)
                || !(target instanceof LivingEntity victim)
                || !(spider.getWorld() instanceof ServerWorld world)) {
            return;
        }
        int cooldown = MinecraftDifficultyProfiles.forWorld(world).spider().cobwebCooldownTicks();
        long now = world.getTime();
        if (cooldown == 0 || now < threatdirector$nextCobwebTick) {
            return;
        }
        threatdirector$nextCobwebTick = now + cooldown;
        SpiderDifficultyAbilities.tryPlaceCobweb(spider, victim);
    }
}
