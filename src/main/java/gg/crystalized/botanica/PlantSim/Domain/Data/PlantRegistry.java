package gg.crystalized.botanica.PlantSim.Domain.Data;

import java.util.Map;

public final class PlantRegistry {
    private final java.util.Map<String, PlantSpec> byId;
    public PlantRegistry(Map<String, PlantSpec> m) { this.byId = java.util.Collections.unmodifiableMap(m); }
    public PlantSpec get(String id) { return byId.get(id); }
    public java.util.Collection<PlantSpec> all() { return byId.values(); }
}
