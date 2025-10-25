package gg.crystalized.botanica.PlantSim.Domain;

import gg.crystalized.botanica.World.BlockPos;
import gg.crystalized.botanica.World.ChunkRef;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class LocalPlantRepo implements PlantRepo {
    private final Map<ChunkRef, Map<BlockPos, PlantInstance>> byChunk = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<List<PlantInstance>> loadByChunk(ChunkRef chunk) {
        Map<BlockPos, PlantInstance> m = byChunk.computeIfAbsent(chunk, k -> new ConcurrentHashMap<>());
        return CompletableFuture.completedFuture(new ArrayList<>(m.values()));
    }

    @Override
    public CompletableFuture<Void> upsertAll(Collection<PlantInstance> plants) {
        for (PlantInstance p : plants) {
            byChunk.computeIfAbsent(p.chunk, k -> new ConcurrentHashMap<>()).put(p.pos, p);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> flush() { return CompletableFuture.completedFuture(null); }

    @Override
    public Iterable<ChunkRef> allLoadedChunks() { return byChunk.keySet(); }

    @Override public Optional<PlantInstance> findByPosition(ChunkRef chunk, BlockPos pos) {
        Map<BlockPos, PlantInstance> m = byChunk.get(chunk);
        if (m == null) return Optional.empty();
        return Optional.ofNullable(m.get(pos));
    }
    
    // Synchronous methods for immediate access
    @Override
    public PlantInstance get(BlockPos pos) {
        ChunkRef chunk = ChunkRef.of(pos);
        Map<BlockPos, PlantInstance> m = byChunk.get(chunk);
        return m != null ? m.get(pos) : null;
    }
    
    @Override
    public void upsert(PlantInstance plant) {
        byChunk.computeIfAbsent(plant.chunk, k -> new ConcurrentHashMap<>()).put(plant.pos, plant);
    }
    
    @Override
    public void remove(PlantInstance plant) {
        Map<BlockPos, PlantInstance> m = byChunk.get(plant.chunk);
        if (m != null) {
            m.remove(plant.pos);
        }
    }
    
    @Override
    public List<PlantInstance> findBySoilPos(BlockPos soilPos) {
        List<PlantInstance> result = new ArrayList<>();
        for (Map<BlockPos, PlantInstance> chunkMap : byChunk.values()) {
            for (PlantInstance plant : chunkMap.values()) {
                if (plant.soilPos.equals(soilPos)) {
                    result.add(plant);
                }
            }
        }
        return result;
    }
    
    @Override
    public List<PlantInstance> getAll() {
        List<PlantInstance> result = new ArrayList<>();
        for (Map<BlockPos, PlantInstance> chunkMap : byChunk.values()) {
            result.addAll(chunkMap.values());
        }
        return result;
    }
}
