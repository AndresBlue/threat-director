package com.andresblue.threatdirector.breach;

import com.andresblue.threatdirector.director.EncounterState;
import com.andresblue.threatdirector.role.TacticalRole;
import com.andresblue.threatdirector.tactical.BlockPoint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Bounded, read-only breach simulation. A successful result is only a description
 * of what could be removed; this class cannot mutate a Minecraft world.
 */
public final class BreachPlanner {
    public BreachPlan simulate(BreachRequest request, BreachWorldView world) {
        BreachPlan denied = checkPreconditions(request, world);
        if (denied != null) return denied;

        Comparator<Node> order = Comparator
                .comparingInt((Node node) -> node.breaks.size())
                .thenComparingInt(node -> node.budgetCost)
                .thenComparingInt(node -> node.path.size())
                .thenComparing(node -> node.position)
                .thenComparing(Node::breakSignature);
        PriorityQueue<Node> open = new PriorityQueue<>(order);
        open.add(new Node(request.mobStart(), List.of(), List.of(request.mobStart()), 0));
        Map<StateKey, Cost> best = new HashMap<>();
        int visited = 0;
        String lastRejection = "no_reachable_route";

        while (!open.isEmpty()) {
            if (visited >= Math.max(1, request.maximumVisitedNodes())) {
                return result(BreachDecision.SEARCH_LIMIT_REACHED, List.of(), "search_limit_reached",
                        List.of(), ProtectionStatus.UNKNOWN, false, 0, visited);
            }
            Node node = open.remove();
            StateKey stateKey = new StateKey(node.position, Set.copyOf(node.breaks));
            Cost nodeCost = new Cost(node.breaks.size(), node.budgetCost, node.path.size());
            Cost known = best.get(stateKey);
            if (known != null && known.compareTo(nodeCost) <= 0) continue;
            best.put(stateKey, nodeCost);
            visited++;

            if (goalReached(node.position, request.target())) {
                BreachDecision decision = node.breaks.isEmpty()
                        ? BreachDecision.NO_BREACH_REQUIRED : BreachDecision.WOULD_BREACH;
                String reason = node.breaks.isEmpty() ? "normal_route_available" : "bounded_route_opened";
                return result(decision, node.breaks, reason, node.path, ProtectionStatus.SAFE,
                        true, node.budgetCost, visited);
            }

            for (BlockPoint neighbor : neighbors(node.position)) {
                if (!withinBounds(neighbor, request)) continue;
                Set<BlockPoint> assumedRemoved = new HashSet<>(node.breaks);
                List<BlockPoint> obstructions = new ArrayList<>(world.obstructionsForStep(
                        node.position, neighbor, Set.copyOf(assumedRemoved)));
                obstructions.sort(BlockPoint::compareTo);
                List<BlockPoint> nextBreaks = new ArrayList<>(node.breaks);
                int nextBudget = node.budgetCost;
                boolean allowed = true;

                for (BlockPoint obstruction : obstructions) {
                    if (assumedRemoved.contains(obstruction)) continue;
                    BlockCheck check = checkBlock(obstruction, request, world);
                    if (!check.allowed) {
                        lastRejection = check.reason;
                        allowed = false;
                        break;
                    }
                    nextBreaks.add(obstruction);
                    assumedRemoved.add(obstruction);
                    nextBudget += check.cost;
                    if (nextBreaks.size() > request.maximumBlocks()) {
                        lastRejection = "block_count_budget_exceeded";
                        allowed = false;
                        break;
                    }
                    if (nextBudget > request.blockBudget()) {
                        lastRejection = "block_cost_budget_exceeded";
                        allowed = false;
                        break;
                    }
                }
                if (!allowed || !world.isSafeStandable(neighbor, Set.copyOf(assumedRemoved))) {
                    if (allowed && "no_reachable_route".equals(lastRejection)) {
                        lastRejection = "unsafe_route_after_removal";
                    }
                    continue;
                }
                nextBreaks.sort(BlockPoint::compareTo);
                List<BlockPoint> nextPath = new ArrayList<>(node.path);
                nextPath.add(neighbor);
                open.add(new Node(neighbor, List.copyOf(nextBreaks), List.copyOf(nextPath), nextBudget));
            }
        }
        ProtectionStatus protection = lastRejection.contains("claim") || lastRejection.contains("spawn")
                ? ProtectionStatus.PROTECTED : lastRejection.contains("unknown")
                ? ProtectionStatus.UNKNOWN : ProtectionStatus.SAFE;
        return result(BreachDecision.NO_ROUTE, List.of(), lastRejection, List.of(), protection,
                false, 0, visited);
    }

    private BreachPlan checkPreconditions(BreachRequest request, BreachWorldView world) {
        if (request.policy() == BreachPolicy.DISABLED) return denied("policy_disabled", ProtectionStatus.SAFE);
        if (!request.tacticalDestructionRule()) return denied("server_rule_disabled", ProtectionStatus.SAFE);
        if (request.intensity() < request.minimumIntensity()) return denied("intensity_too_low", ProtectionStatus.SAFE);
        if (request.encounterState() != EncounterState.ENGAGING
                && request.encounterState() != EncounterState.CLIMAX) {
            return denied("invalid_phase", ProtectionStatus.SAFE);
        }
        if (request.role() != TacticalRole.BREACHER) return denied("requires_breacher_role", ProtectionStatus.SAFE);
        if (!request.globalCooldownReady() || !request.mobCooldownReady()) {
            return denied("cooldown_active", ProtectionStatus.SAFE);
        }
        if (request.maximumBlocks() <= 0 || request.blockBudget() <= 0) {
            return denied("no_block_budget", ProtectionStatus.SAFE);
        }
        ClaimStatus targetClaim = world.claimAt(request.target());
        if (targetClaim == ClaimStatus.PROTECTED) return denied("target_claim_protected", ProtectionStatus.PROTECTED);
        if (targetClaim == ClaimStatus.UNKNOWN) return denied("target_claim_unknown", ProtectionStatus.UNKNOWN);
        if (world.isSpawnProtected(request.target())) return denied("target_spawn_protected", ProtectionStatus.PROTECTED);
        return null;
    }

    private BlockCheck checkBlock(BlockPoint position, BreachRequest request, BreachWorldView world) {
        ClaimStatus claim = world.claimAt(position);
        if (claim == ClaimStatus.PROTECTED) return BlockCheck.denied("block_claim_protected");
        if (claim == ClaimStatus.UNKNOWN) return BlockCheck.denied("block_claim_unknown");
        if (world.isSpawnProtected(position)) return BlockCheck.denied("block_spawn_protected");
        double claimDistance = world.distanceToNearestClaim(position);
        if (Double.isNaN(claimDistance)) return BlockCheck.denied("claim_distance_unknown");
        if (claimDistance < request.minimumClaimDistance()) return BlockCheck.denied("too_close_to_claim");

        BlockSnapshot block = world.blockAt(position);
        if (!Double.isFinite(block.hardness()) || block.hardness() < 0.0
                || block.hardness() > request.maximumHardness()) return BlockCheck.denied("hardness_forbidden");
        if (block.blockEntity()) return BlockCheck.denied("block_entity_forbidden");
        if (block.container()) return BlockCheck.denied("container_forbidden");
        if (block.sensitive()) return BlockCheck.denied("sensitive_block_forbidden");
        if (block.hazardousAfterRemoval()) return BlockCheck.denied("hazardous_removal");
        if (request.policy() == BreachPolicy.SOFT_BLOCKS_ONLY && !block.soft()) {
            return BlockCheck.denied("not_soft_block");
        }
        if (request.policy() == BreachPolicy.PLAYER_PLACED_PROTECTION && block.playerPlaced()) {
            return BlockCheck.denied("player_placed_protected");
        }
        int cost = 1_000 + (int) Math.round(250.0 * block.hardness());
        return new BlockCheck(true, "allowed", cost);
    }

    private static List<BlockPoint> neighbors(BlockPoint center) {
        List<BlockPoint> values = new ArrayList<>(12);
        int[][] horizontal = {{-1, 0}, {0, -1}, {0, 1}, {1, 0}};
        for (int yDelta = -1; yDelta <= 1; yDelta++) {
            for (int[] delta : horizontal) {
                values.add(new BlockPoint(center.x() + delta[0], center.y() + yDelta, center.z() + delta[1]));
            }
        }
        values.sort(BlockPoint::compareTo);
        return values;
    }

    private static boolean withinBounds(BlockPoint value, BreachRequest request) {
        return Math.abs(value.x() - request.mobStart().x()) <= request.horizontalRadius()
                && Math.abs(value.z() - request.mobStart().z()) <= request.horizontalRadius()
                && Math.abs(value.y() - request.mobStart().y()) <= request.verticalRadius();
    }

    private static boolean goalReached(BlockPoint position, BlockPoint target) {
        return position.horizontalDistance(target) <= 1.5 && Math.abs(position.y() - target.y()) <= 1;
    }

    private static BreachPlan denied(String reason, ProtectionStatus status) {
        return result(BreachDecision.DENIED, List.of(), reason, List.of(), status, false, 0, 0);
    }

    private static BreachPlan result(BreachDecision decision, List<BlockPoint> blocks, String reason,
                                     List<BlockPoint> route, ProtectionStatus protection, boolean safe,
                                     int budget, int visited) {
        return new BreachPlan(decision, blocks, reason, route, blocks.size(), protection,
                safe, budget, visited);
    }

    private record BlockCheck(boolean allowed, String reason, int cost) {
        private static BlockCheck denied(String reason) {
            return new BlockCheck(false, reason, 0);
        }
    }

    private record Node(BlockPoint position, List<BlockPoint> breaks, List<BlockPoint> path, int budgetCost) {
        private String breakSignature() {
            return breaks.toString();
        }
    }

    private record StateKey(BlockPoint position, Set<BlockPoint> breaks) {}

    private record Cost(int blocks, int budget, int pathLength) implements Comparable<Cost> {
        @Override
        public int compareTo(Cost other) {
            int result = Integer.compare(blocks, other.blocks);
            if (result != 0) return result;
            result = Integer.compare(budget, other.budget);
            return result != 0 ? result : Integer.compare(pathLength, other.pathLength);
        }
    }
}
