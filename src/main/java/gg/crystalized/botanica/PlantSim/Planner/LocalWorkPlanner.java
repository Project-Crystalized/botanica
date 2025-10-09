package gg.crystalized.botanica.PlantSim.Planner;

import gg.crystalized.botanica.PlantSim.Domain.PlantInstance;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class LocalWorkPlanner implements WorkPlanner {
    private final PriorityQueue<PlantInstance> heap = new PriorityQueue<>(Comparator.comparing(p -> p.nextUpdateAt));
    private final ConcurrentLinkedQueue<PlantInstance> dirty = new ConcurrentLinkedQueue<>();
    private final Set<UUID> inHeap = new HashSet<>();

    @Override public synchronized void addPlant(PlantInstance p) { if (inHeap.add(p.id)) heap.add(p); }
    @Override public synchronized void removePlant(PlantInstance p) { heap.remove(p); inHeap.remove(p.id); }

    @Override public void wakeNow(PlantInstance p) { dirty.add(p); }

    @Override
    public synchronized List<PlantInstance> takeDue(int max, Instant now) {
        List<PlantInstance> out = new ArrayList<>(max);
        int dirtyBudget = Math.max(1, max / 4);
        for (int i = 0; i < dirtyBudget; i++) {
            PlantInstance d = dirty.poll();
            if (d == null) break; out.add(d);
        }
        while (out.size() < max && !heap.isEmpty()) {
            PlantInstance p = heap.peek();
            if (p.nextUpdateAt != null && p.nextUpdateAt.isAfter(now)) break;
            heap.poll(); out.add(p);
        }
        return out;
    }

    public synchronized void reschedule(PlantInstance p) { if (inHeap.add(p.id)) heap.add(p); else { heap.remove(p); heap.add(p); } }
}