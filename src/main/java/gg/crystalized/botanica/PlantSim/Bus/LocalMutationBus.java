package gg.crystalized.botanica.PlantSim.Bus;

import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;

public class LocalMutationBus implements MutationBus {
    private final ConcurrentLinkedQueue<Mutation> q = new ConcurrentLinkedQueue<>();


    @Override public void queue(Mutation mutation) { if (mutation != null) q.add(mutation); }
    @Override public void queueAll(Collection<? extends Mutation> mutations) { if (mutations != null && !mutations.isEmpty()) q.addAll(mutations); }


    @Override public int drain(Collection<? super Mutation> target, int maxElements) {
        Mutation m;
        int n = 0;
        while (n < maxElements && (m = q.poll()) != null) { target.add(m); n++; }
        return n;
    }

    @Override public int size() { return q.size(); }
}
