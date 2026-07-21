package com.andresblue.threatdirector.role;

import com.andresblue.threatdirector.trait.MobProfile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RoleAssignmentSystem {
    public RoleAssignment assign(List<MobProfile> inputMobs, List<RoleSlot> inputSlots,
                                 Map<UUID, RoleContext> contexts) {
        if (inputMobs.size() > 10 || inputSlots.size() > 10) {
            throw new IllegalArgumentException("MVP role assignment is capped at ten mobs and slots");
        }
        List<MobProfile> mobs = new ArrayList<>(inputMobs);
        mobs.sort(Comparator.comparing(mob -> mob.id().toString()));
        List<RoleSlot> slots = new ArrayList<>(inputSlots);
        slots.sort(Comparator.comparing(RoleSlot::id));

        Map<State, Solution> memo = new HashMap<>();
        Solution solution = solve(0, 0, mobs, slots, contexts, memo);
        Map<String, UUID> result = new LinkedHashMap<>();
        for (int slotIndex = 0; slotIndex < slots.size(); slotIndex++) {
            int mobIndex = solution.mobIndices[slotIndex];
            if (mobIndex >= 0) {
                result.put(slots.get(slotIndex).id(), mobs.get(mobIndex).id());
            }
        }
        return new RoleAssignment(result, solution.requiredCovered, solution.score);
    }

    private Solution solve(int slotIndex, int usedMask, List<MobProfile> mobs, List<RoleSlot> slots,
                           Map<UUID, RoleContext> contexts, Map<State, Solution> memo) {
        if (slotIndex == slots.size()) {
            int[] empty = new int[slots.size()];
            Arrays.fill(empty, -1);
            return new Solution(0, 0, empty);
        }
        State state = new State(slotIndex, usedMask);
        Solution cached = memo.get(state);
        if (cached != null) return cached;

        RoleSlot slot = slots.get(slotIndex);
        Solution suffix = solve(slotIndex + 1, usedMask, mobs, slots, contexts, memo);
        Solution best = suffix.withChoice(slotIndex, -1, 0, 0);
        for (int mobIndex = 0; mobIndex < mobs.size(); mobIndex++) {
            if ((usedMask & (1 << mobIndex)) != 0) continue;
            MobProfile mob = mobs.get(mobIndex);
            RoleContext context = contexts.getOrDefault(mob.id(), RoleContext.fullyReachable());
            int roleScore = RoleScorer.score(mob, slot.role(), context);
            if (roleScore < 0) continue;
            Solution remainder = solve(slotIndex + 1, usedMask | (1 << mobIndex), mobs, slots, contexts, memo);
            Solution candidate = remainder.withChoice(slotIndex, mobIndex, roleScore, slot.required() ? 1 : 0);
            if (candidate.betterThan(best)) best = candidate;
        }
        memo.put(state, best);
        return best;
    }

    private record State(int slotIndex, int usedMask) {}

    private static final class Solution {
        private final int requiredCovered;
        private final int score;
        private final int[] mobIndices;

        private Solution(int requiredCovered, int score, int[] mobIndices) {
            this.requiredCovered = requiredCovered;
            this.score = score;
            this.mobIndices = mobIndices;
        }

        private Solution withChoice(int slotIndex, int mobIndex, int addedScore, int addedRequired) {
            int[] choices = mobIndices.clone();
            choices[slotIndex] = mobIndex;
            return new Solution(requiredCovered + addedRequired, score + addedScore, choices);
        }

        private boolean betterThan(Solution other) {
            if (requiredCovered != other.requiredCovered) return requiredCovered > other.requiredCovered;
            if (score != other.score) return score > other.score;
            for (int index = 0; index < mobIndices.length; index++) {
                int left = mobIndices[index] < 0 ? Integer.MAX_VALUE : mobIndices[index];
                int right = other.mobIndices[index] < 0 ? Integer.MAX_VALUE : other.mobIndices[index];
                if (left != right) return left < right;
            }
            return false;
        }
    }
}
