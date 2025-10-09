package gg.crystalized.botanica.PlantSim.Planner;

import gg.crystalized.botanica.PlantSim.Domain.PlantInstance;


import java.time.Instant;
import java.util.List;

/** Plant-centric planner with a due-time heap and a small dirty queue for fast-lane events. */
public interface WorkPlanner {
    void addPlant(PlantInstance p);
    void removePlant(PlantInstance p);
    void wakeNow(PlantInstance p);
    List<PlantInstance> takeDue(int max, Instant now);
}