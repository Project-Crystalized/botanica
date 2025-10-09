package gg.crystalized.botanica.PlantSim.Domain.Data;

import java.util.Map;

public final class PlantSetRegistry {
    private final java.util.Map<String, PlantSetSpec> byId;
    public PlantSetRegistry(Map<String, PlantSetSpec> m) { this.byId = java.util.Collections.unmodifiableMap(m); }
    public PlantSetSpec get(String id) { return byId.get(id); }
    public java.util.Collection<PlantSetSpec> all() { return byId.values(); }
}
