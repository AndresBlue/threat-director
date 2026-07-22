package com.andresblue.threatdirector.mixin;

import com.andresblue.threatdirector.difficulty.SpiderDifficultyAbilities;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpiderEntity.class)
public abstract class SpiderDifficultyMixin {
    @Inject(method = "initialize", at = @At("RETURN"))
    private void threatdirector$applyDifficultyBuffs(ServerWorldAccess world, LocalDifficulty difficulty,
                                                     SpawnReason spawnReason, EntityData entityData,
                                                     NbtCompound entityNbt,
                                                     CallbackInfoReturnable<EntityData> callback) {
        SpiderDifficultyAbilities.applySpawnBuffs((SpiderEntity) (Object) this);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void threatdirector$refreshDifficultyBuffs(CallbackInfo callback) {
        SpiderEntity spider = (SpiderEntity) (Object) this;
        if (Math.floorMod(spider.age + spider.getUuid().hashCode(), 100) == 0) {
            SpiderDifficultyAbilities.applySpawnBuffs(spider);
        }
    }
}
