package com.andresblue.threatdirector.encounter;

import com.andresblue.threatdirector.role.RoleAssignment;
import com.andresblue.threatdirector.role.RoleAssignmentSystem;
import com.andresblue.threatdirector.role.RoleContext;
import com.andresblue.threatdirector.role.RoleSlot;
import com.andresblue.threatdirector.role.TacticalRole;
import com.andresblue.threatdirector.trait.MobProfile;
import com.andresblue.threatdirector.trait.MobTrait;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class VanillaRoleAssignment {
    private final RoleAssignmentSystem assignmentSystem = new RoleAssignmentSystem();

    public Map<UUID, TacticalRole> assign(List<MobProfile> profiles) {
        List<MobProfile> optimizationProfiles = profiles.stream()
                .sorted(Comparator.comparing(profile -> profile.id().toString()))
                .limit(10)
                .toList();
        List<RoleSlot> slots = slotsFor(optimizationProfiles);
        Map<UUID, RoleContext> contexts = new HashMap<>();
        optimizationProfiles.forEach(profile ->
                contexts.put(profile.id(), RoleContext.fullyReachable()));
        RoleAssignment assignment = assignmentSystem.assign(optimizationProfiles, slots, contexts);

        Map<String, TacticalRole> roleBySlot = new HashMap<>();
        slots.forEach(slot -> roleBySlot.put(slot.id(), slot.role()));
        Map<UUID, TacticalRole> roles = new LinkedHashMap<>();
        assignment.mobBySlot().forEach((slot, mobId) -> roles.put(mobId, roleBySlot.get(slot)));

        int mobileFallbackIndex = 0;
        for (MobProfile profile : profiles) {
            if (roles.containsKey(profile.id())) {
                continue;
            }
            TacticalRole fallback;
            if (profile.has(MobTrait.RANGED)) {
                fallback = TacticalRole.HARASSER;
            } else if (profile.has(MobTrait.EXPLOSIVE)) {
                fallback = TacticalRole.INTERCEPTOR;
            } else if (profile.has(MobTrait.FAST) || profile.has(MobTrait.CLIMBING)) {
                fallback = mobileFallbackIndex++ % 2 == 0
                        ? TacticalRole.FLANKER
                        : TacticalRole.INTERCEPTOR;
            } else {
                fallback = TacticalRole.VANGUARD;
            }
            roles.put(profile.id(), fallback);
        }
        return Map.copyOf(roles);
    }

    private static List<RoleSlot> slotsFor(List<MobProfile> profiles) {
        List<RoleSlot> slots = new ArrayList<>();
        if (profiles.stream().anyMatch(profile -> profile.has(MobTrait.MELEE)
                && !profile.has(MobTrait.EXPLOSIVE))) {
            slots.add(new RoleSlot("vanguard-0", TacticalRole.VANGUARD, true));
        }
        if (profiles.stream().anyMatch(profile -> profile.has(MobTrait.RANGED))) {
            slots.add(new RoleSlot("harasser-0", TacticalRole.HARASSER, true));
        }
        if (profiles.stream().anyMatch(profile -> profile.has(MobTrait.FAST)
                || profile.has(MobTrait.CLIMBING))) {
            slots.add(new RoleSlot("flanker-0", TacticalRole.FLANKER, true));
        }
        if (profiles.size() >= 4 && profiles.stream().anyMatch(profile ->
                profile.has(MobTrait.FAST)
                        || profile.has(MobTrait.CLIMBING)
                        || profile.has(MobTrait.EXPLOSIVE))) {
            slots.add(new RoleSlot("interceptor-0", TacticalRole.INTERCEPTOR, true));
        }
        return slots;
    }
}
