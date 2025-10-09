package gg.crystalized.botanica.PlantSim.Bus;

import java.util.Collection;

public interface MutationBus {
    void queue(Mutation mutation);
    void queueAll(Collection<? extends Mutation> mutations);
    int drain(Collection<? super Mutation> target, int maxElements);
    int size();
}
