package gg.crystalized.botanica.Soil.Domain;

import gg.crystalized.botanica.World.BlockPos;
import gg.crystalized.botanica.World.ChunkRef;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class LocalSoilRepo implements SoilRepo {
    private final Map<ChunkRef, Map<BlockPos, SoilInstance>> byChunk = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<List<SoilInstance>> loadByChunk(ChunkRef chunk) {
        var m = byChunk.computeIfAbsent(chunk, k -> new ConcurrentHashMap<>());
        return CompletableFuture.completedFuture(new ArrayList<>(m.values()));
    }

    @Override
    public CompletableFuture<Void> upsertAll(Collection<SoilInstance> soils) {
        for (SoilInstance s : soils) {
            byChunk.computeIfAbsent(s.chunk, k -> new ConcurrentHashMap<>()).put(s.pos, s);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> flush() { return CompletableFuture.completedFuture(null); }

    @Override
    public Iterable<ChunkRef> allLoadedChunks() { return byChunk.keySet(); }

    @Override
    public Optional<SoilInstance> findByPosition(ChunkRef chunk, BlockPos pos) {
        var m = byChunk.get(chunk);
        return m == null ? Optional.empty() : Optional.ofNullable(m.get(pos));
    }
    
    // Synchronous methods for immediate access
    @Override
    public SoilInstance get(BlockPos pos) {
        ChunkRef chunk = ChunkRef.of(pos);
        Map<BlockPos, SoilInstance> m = byChunk.get(chunk);
        return m != null ? m.get(pos) : null;
    }
    
    @Override
    public void upsert(SoilInstance soil) {
        byChunk.computeIfAbsent(soil.chunk, k -> new ConcurrentHashMap<>()).put(soil.pos, soil);
    }
    
    @Override
    public void delete(BlockPos pos) {
        ChunkRef chunk = ChunkRef.of(pos);
        Map<BlockPos, SoilInstance> m = byChunk.get(chunk);
        if (m != null) {
            m.remove(pos);
        }
    }
}
