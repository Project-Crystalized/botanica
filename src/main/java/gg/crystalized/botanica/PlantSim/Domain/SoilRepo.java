package gg.crystalized.botanica.PlantSim.Domain;

import gg.crystalized.botanica.PlantSim.World.BlockPos;
import gg.crystalized.botanica.PlantSim.World.ChunkRef;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface SoilRepo {
    CompletableFuture<List<SoilInstance>> loadByChunk(ChunkRef chunk);
    CompletableFuture<Void> upsertAll(Collection<SoilInstance> soils);
    CompletableFuture<Void> flush();
    Iterable<ChunkRef> allLoadedChunks();

    Optional<SoilInstance> findByPosition(ChunkRef chunk, BlockPos pos);

    // Synchronous methods for immediate access
    SoilInstance get(BlockPos pos);
    void upsert(SoilInstance soil);

    // Convenience helpers (optional to include in the interface)
    default Optional<SoilInstance> findByPosition(BlockPos pos) {
        return findByPosition(ChunkRef.of(pos), pos);
    }
}
