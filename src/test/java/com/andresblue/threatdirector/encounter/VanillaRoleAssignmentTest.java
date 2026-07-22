package com.andresblue.threatdirector.encounter;

import com.andresblue.threatdirector.role.TacticalRole;
import com.andresblue.threatdirector.trait.MobProfile;
import com.andresblue.threatdirector.trait.MobTrait;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class VanillaRoleAssignmentTest {
    @Test
    void largeHordesReceiveRolesWithoutExceedingTheOptimizerLimit() {
        List<MobProfile> profiles = new ArrayList<>();
        for (int index = 0; index < 23; index++) {
            profiles.add(new MobProfile(
                    new UUID(0, index + 1),
                    "minecraft:pillager",
                    Set.of(MobTrait.RANGED),
                    1, 24, 0, 5, .35, 1, true));
        }

        Map<UUID, TacticalRole> roles = new VanillaRoleAssignment().assign(profiles);

        assertEquals(profiles.size(), roles.size());
        profiles.forEach(profile -> assertNotNull(roles.get(profile.id())));
    }
}
