package gg.crystalized.botanica.PlantSim.Domain;

import gg.crystalized.botanica.World.BlockPos;
import gg.crystalized.botanica.World.ChunkRef;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface PlantRepo {
    CompletableFuture<List<PlantInstance>> loadByChunk(ChunkRef chunk);
    CompletableFuture<Void> upsertAll(Collection<PlantInstance> plants);
    CompletableFuture<Void> flush();
    Iterable<ChunkRef> allLoadedChunks();
    Optional<PlantInstance> findByPosition(ChunkRef chunk, BlockPos pos);
    
    // Synchronous methods for immediate access
    PlantInstance get(BlockPos pos);
    void upsert(PlantInstance plant);
    void remove(PlantInstance plant);
    List<PlantInstance> findBySoilPos(BlockPos soilPos);
    List<PlantInstance> getAll();
}