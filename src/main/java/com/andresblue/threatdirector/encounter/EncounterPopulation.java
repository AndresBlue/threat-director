package com.andresblue.threatdirector.encounter;

import com.andresblue.threatdirector.trait.MobProfile;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable result of recruiting and, when necessary, safely spawning an encounter group. */
public record EncounterPopulation(
        List<Member> members,
        int spawnAttempts,
        List<String> diagnostics,
        int planningOperations) {

    /** Backwards-compatible constructor for immutable snapshots created outside the world service. */
    public EncounterPopulation(List<Member> members, int spawnAttempts, List<String> diagnostics) {
        this(members, spawnAttempts, diagnostics, spawnAttempts);
    }

    public EncounterPopulation {
        members = List.copyOf(Objects.requireNonNull(members, "members"));
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        if (spawnAttempts < 0) {
            throw new IllegalArgumentException("spawnAttempts must be non-negative");
        }
        if (planningOperations < spawnAttempts) {
            throw new IllegalArgumentException(
                    "planningOperations must include every spawn attempt");
        }

        Set<UUID> uniqueMembers = new HashSet<>();
        for (Member member : members) {
            if (!uniqueMembers.add(member.profile().id())) {
                throw new IllegalArgumentException(
                        "Duplicate encounter member: " + member.profile().id());
            }
        }
    }

    public int recruitedCount() {
        return count(Origin.RECRUITED);
    }

    public int spawnedCount() {
        return count(Origin.DIRECTOR_SPAWNED);
    }

    public boolean meetsMinimum(int minimum) {
        return minimum >= 0 && members.size() >= minimum;
    }

    private int count(Origin origin) {
        return (int) members.stream().filter(member -> member.origin() == origin).count();
    }

    public record Member(MobProfile profile, Origin origin, int threatCost) {
        public Member {
            Objects.requireNonNull(profile, "profile");
            Objects.requireNonNull(origin, "origin");
            if (threatCost <= 0) {
                throw new IllegalArgumentException("threatCost must be positive");
            }
        }
    }

    public enum Origin {
        RECRUITED,
        DIRECTOR_SPAWNED
    }
}
