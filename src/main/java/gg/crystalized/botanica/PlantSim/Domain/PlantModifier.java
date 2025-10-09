package gg.crystalized.botanica.PlantSim.Domain;

import java.time.Instant;

public record PlantModifier(String kind, double multiplier, double flatAdd, Instant expiresAt) {
    public boolean activeAt(Instant t) { return expiresAt == null || !expiresAt.isBefore(t); }
}