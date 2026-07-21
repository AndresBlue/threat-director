package com.andresblue.threatdirector.ai;

import net.minecraft.entity.mob.MobEntity;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class MobOrderStore {
    private final Map<UUID, MobOrder> orders = new ConcurrentHashMap<>();

    public void issue(MobEntity mob, MobOrder order) {
        orders.put(mob.getUuid(), order);
    }

    public Optional<MobOrder> current(MobEntity mob, long serverTick) {
        MobOrder order = orders.get(mob.getUuid());
        if (order != null && order.expired(serverTick)) {
            orders.remove(mob.getUuid(), order);
            return Optional.empty();
        }
        return Optional.ofNullable(order);
    }

    public void clear(MobEntity mob) {
        orders.remove(mob.getUuid());
    }

    public void clearEncounter(UUID encounterId) {
        orders.entrySet().removeIf(entry -> entry.getValue().encounterId().equals(encounterId));
    }

    public void clearMobs(Collection<UUID> mobIds) {
        mobIds.forEach(orders::remove);
    }

    public void clearAll() {
        orders.clear();
    }

    public int size() {
        return orders.size();
    }

    public Collection<UUID> targetPlayerIds(UUID encounterId) {
        return orders.values().stream()
                .filter(order -> order.encounterId().equals(encounterId))
                .map(MobOrder::targetPlayerId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }
}
