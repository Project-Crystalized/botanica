package gg.crystalized.botanica.Soil.Data;

import java.util.Map;

public final class SoilRegistry {
    private final java.util.Map<String, SoilSpec> byId;
    public SoilRegistry(Map<String, SoilSpec> m) { this.byId = java.util.Collections.unmodifiableMap(m); }
    public SoilSpec get(String id) { return byId.get(id); }
    public java.util.Collection<SoilSpec> all() { return byId.values(); }
}