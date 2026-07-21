package com.andresblue.threatdirector.role;

import com.andresblue.threatdirector.trait.MobProfile;
import com.andresblue.threatdirector.trait.MobTrait;
import com.andresblue.threatdirector.trait.MobTraitProvider;
import com.andresblue.threatdirector.trait.MobTraitRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleAndTraitTest {
    @Test
    void vanillaTraitsMatchMvpContractAndProviderFailureIsSafe() {
        MobTraitRegistry registry = MobTraitRegistry.vanillaMvp().withProvider(new MobTraitProvider() {
            @Override public String id() { return "broken"; }
            @Override public TraitPatch traitsFor(String entityTypeId) { throw new IllegalStateException(); }
        });
        var zombie = registry.resolve("minecraft:zombie");
        assertEquals(Set.of(MobTrait.MELEE, MobTrait.TANKY, MobTrait.BREACH_CAPABLE), zombie.traits());
        assertEquals(1, zombie.warnings().size());
        assertEquals(Set.of(MobTrait.RANGED), registry.resolve("minecraft:skeleton").traits());
    }

    @Test
    void globalAssignmentCoversRequiredRolesDeterministically() {
        MobProfile zombie = mob(1, "minecraft:zombie", Set.of(MobTrait.MELEE, MobTrait.TANKY), .23, 3);
        MobProfile skeleton = mob(2, "minecraft:skeleton", Set.of(MobTrait.RANGED), .25, 2);
        MobProfile spider = mob(3, "minecraft:spider", Set.of(MobTrait.MELEE, MobTrait.FAST, MobTrait.CLIMBING), .30, 2);
        List<RoleSlot> slots = List.of(
                new RoleSlot("1-front", TacticalRole.VANGUARD, true),
                new RoleSlot("2-range", TacticalRole.HARASSER, true),
                new RoleSlot("3-exit", TacticalRole.INTERCEPTOR, true));

        RoleAssignment result = new RoleAssignmentSystem().assign(
                List.of(spider, zombie, skeleton), slots, Map.of());

        assertEquals(3, result.requiredSlotsCovered());
        assertEquals(zombie.id(), result.mobBySlot().get("1-front"));
        assertEquals(skeleton.id(), result.mobBySlot().get("2-range"));
        assertEquals(spider.id(), result.mobBySlot().get("3-exit"));
        assertTrue(result.totalScore() > 0);
    }

    private static MobProfile mob(int suffix, String type, Set<MobTrait> traits, double speed, double damage) {
        UUID id = UUID.fromString("00000000-0000-0000-0000-00000000000" + suffix);
        return new MobProfile(id, type, traits, 1, 20, 0, damage, speed, 1, true);
    }
}
