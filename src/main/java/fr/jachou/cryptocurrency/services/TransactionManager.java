package fr.jachou.cryptocurrency.services;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TransactionManager {
    public static class Txn {
        public final Instant time = Instant.now();
        public final UUID actor;
        public final String type; // BUY, SELL, TRANSFER, CONVERT, ADMIN
        public final String details;
        public Txn(UUID actor, String type, String details) {
            this.actor = actor; this.type = type; this.details = details;
        }
    }

    private final Map<UUID, Deque<Txn>> history = new ConcurrentHashMap<>();

    public void record(UUID uuid, String type, String details){
        history.computeIfAbsent(uuid, k -> new ArrayDeque<>()).addFirst(new Txn(uuid, type, details));
        Deque<Txn> deque = history.get(uuid);
        while (deque.size() > 50) deque.removeLast();
    }

    public List<Txn> getRecent(UUID uuid, int limit){
        Deque<Txn> deque = history.getOrDefault(uuid, new ArrayDeque<>());
        List<Txn> list = new ArrayList<>();
        int i = 0; for (Txn t : deque) { if (i++>=limit) break; list.add(t); }
        return list;
    }
}
