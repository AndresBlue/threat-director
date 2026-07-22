package com.andresblue.threatdirector.encounter;

import com.andresblue.threatdirector.trait.MobProfile;
import com.andresblue.threatdirector.trait.MobTraitRegistry;
import com.andresblue.threatdirector.trait.TraitResolution;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;

/** Bridges supported vanilla entity types into generic Threat Director profiles. */
public final class VanillaMobAdapter {
    public static final String ZOMBIE_ID = "minecraft:zombie";
    public static final String SKELETON_ID = "minecraft:skeleton";
    public static final String SPIDER_ID = "minecraft:spider";
    public static final String CREEPER_ID = "minecraft:creeper";
    public static final String PILLAGER_ID = "minecraft:pillager";
    public static final String VINDICATOR_ID = "minecraft:vindicator";
    public static final String EVOKER_ID = "minecraft:evoker";
    public static final String RAVAGER_ID = "minecraft:ravager";

    private static final List<String> SUPPORTED_IDS =
            List.of(ZOMBIE_ID, SKELETON_ID, SPIDER_ID, CREEPER_ID,
                    PILLAGER_ID, VINDICATOR_ID, EVOKER_ID, RAVAGER_ID);
    private static final Map<String, EntityType<? extends MobEntity>> ENTITY_TYPES = Map.of(
            ZOMBIE_ID, EntityType.ZOMBIE,
            SKELETON_ID, EntityType.SKELETON,
            SPIDER_ID, EntityType.SPIDER,
            CREEPER_ID, EntityType.CREEPER,
            PILLAGER_ID, EntityType.PILLAGER,
            VINDICATOR_ID, EntityType.VINDICATOR,
            EVOKER_ID, EntityType.EVOKER,
            RAVAGER_ID, EntityType.RAVAGER);

    private final MobTraitRegistry traitRegistry;

    public VanillaMobAdapter(MobTraitRegistry traitRegistry) {
        this.traitRegistry = Objects.requireNonNull(traitRegistry, "traitRegistry");
    }

    /** Returns empty for variants, subclasses registered under another type, and modded entities. */
    public Optional<AdaptedMob> adapt(MobEntity mob) {
        Objects.requireNonNull(mob, "mob");
        String typeId = exactTypeId(mob.getType());
        if (typeId == null) {
            return Optional.empty();
        }

        TraitResolution resolution = traitRegistry.resolve(typeId);
        double maximumHealth = Math.max(0.0, mob.getMaxHealth());
        double healthFraction = maximumHealth == 0.0
                ? 0.0
                : clamp(mob.getHealth() / maximumHealth, 0.0, 1.0);
        MobProfile profile = new MobProfile(
                mob.getUuid(),
                typeId,
                resolution.traits(),
                healthFraction,
                maximumHealth,
                Math.max(0, mob.getArmor()),
                attributeValue(mob, EntityAttributes.GENERIC_ATTACK_DAMAGE),
                attributeValue(mob, EntityAttributes.GENERIC_MOVEMENT_SPEED),
                mob.getType() == EntityType.SKELETON || mob.getType() == EntityType.PILLAGER
                        ? 1.0 : 0.0,
                mob.isAlive() && !mob.isRemoved());
        return Optional.of(new AdaptedMob(profile, resolution.warnings()));
    }

    public boolean supports(MobEntity mob) {
        return mob != null && exactTypeId(mob.getType()) != null;
    }

    public Optional<EntityType<? extends MobEntity>> entityType(String typeId) {
        return Optional.ofNullable(ENTITY_TYPES.get(typeId));
    }

    /**
     * Preserves administrator ordering, removes duplicates and prevents director-created creepers
     * unless the dedicated switch is enabled.
     */
    public List<EntityType<? extends MobEntity>> spawnTypes(
            List<String> allowedMobIds, boolean allowDirectorSpawnedCreepers) {
        if (allowedMobIds == null || allowedMobIds.isEmpty()) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>(allowedMobIds);
        List<EntityType<? extends MobEntity>> result = new ArrayList<>();
        for (String typeId : unique) {
            if (CREEPER_ID.equals(typeId) && !allowDirectorSpawnedCreepers) {
                continue;
            }
            EntityType<? extends MobEntity> type = ENTITY_TYPES.get(typeId);
            if (type != null) {
                result.add(type);
            }
        }
        return List.copyOf(result);
    }

    public static List<String> supportedIds() {
        return SUPPORTED_IDS;
    }

    public static String exactTypeId(EntityType<?> type) {
        if (type == EntityType.ZOMBIE) {
            return ZOMBIE_ID;
        }
        if (type == EntityType.SKELETON) {
            return SKELETON_ID;
        }
        if (type == EntityType.SPIDER) {
            return SPIDER_ID;
        }
        if (type == EntityType.CREEPER) {
            return CREEPER_ID;
        }
        if (type == EntityType.PILLAGER) {
            return PILLAGER_ID;
        }
        if (type == EntityType.VINDICATOR) {
            return VINDICATOR_ID;
        }
        if (type == EntityType.EVOKER) {
            return EVOKER_ID;
        }
        if (type == EntityType.RAVAGER) {
            return RAVAGER_ID;
        }
        return null;
    }

    private static double attributeValue(MobEntity mob, EntityAttribute attribute) {
        EntityAttributeInstance instance = mob.getAttributeInstance(attribute);
        if (instance == null || !Double.isFinite(instance.getValue())) {
            return 0.0;
        }
        return Math.max(0.0, instance.getValue());
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (!Double.isFinite(value)) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record AdaptedMob(MobProfile profile, List<String> warnings) {
        public AdaptedMob {
            Objects.requireNonNull(profile, "profile");
            warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
        }
    }
}
