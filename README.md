# Botanica Simulation System

## Overview

The Botanica simulation system is a real-time, data-driven plant growth and resource management engine for Minecraft. It models plant lifecycles, soil conditions, and player interactions through a robust event-driven architecture.

---

## Guides

New to Botanica development? Start here:

- **[Add Plant Set](README/AddPlantSet.md)** - Step-by-step guide to creating new plants and making them plantable
- **[Create Schematics](README/CreateSchematics.md)** - Learn how to build multi-block plant structures with 3D schematics

---

## Core Features

### Plant Simulation
- **Dynamic Growth System**: Plants progress through configurable growth stages (0-100%) based on time and environmental conditions
- **Growth Gates**: Plants only grow when water and nutrients are within specified ranges
- **Resource Consumption**: Plants consume water and nutrients per second while actively growing
- **Optimal Soil Bonus**: 1.5x growth speed multiplier when planted on preferred soil type
- **Renewable Harvest**: Optional mechanic where plants regress to 2nd-to-last stage instead of being destroyed
- **Random Rotation**: Optional Y-axis rotation (0°, 90°, 180°, 270°) for visual variety in multi-block plants
- **Multi-Block Schematics**: Support for complex 3D plant structures with "reckless growth, surgical removal" philosophy

### Soil Management
- **Resource Tracking**: Per-block water (0-100) and nutrient (0-100) levels
- **Quality Multipliers**: Rolled on creation, affects plant growth rates
- **Soil Types**: Configurable soil types with display names and base block types (SANDY, LOAMY, CLAY)

### Drop System
- **Fertility Bonuses**: Per-plant fertility values modify drop ranges (supports negative values)
- **Configurable Ranges**: Min/max drop quantities with fertility as flat bonus
- **Spatial Distribution**: Configurable drop height and radius (min/max distance from plant)
- **Drop Grouping**: Optional splitting of drops into 2-3 groups at different positions for natural scattering
- **Loot Enchantments**: Harvest tool loot level multiplies fertility bonus

### Interaction System
- **Config-Driven Actions**: `ItemActions.json` maps item IDs to simulation actions (TILL_SOIL, PLANT, HARVEST, ADD_WATER, etc.)
- **Item Transformation**: Support for item consumption and item giving (e.g., water bucket → bucket)
- **Action Validation**: Items are validated against context (can't plant without soil, can't harvest incomplete plants)

### World Integration
- **Mutation Bus**: All world changes (block placement, item drops, sounds) are queued and applied asynchronously
- **Mutation Applier**: Caps world changes per tick to prevent lag spikes
- **Spatial Indexing**: Fast O(1) lookup for multi-block plants using `SchematicBlockIndex`
- **Block Break Integration**: Breaking non-whitelist blocks removes entire plant and clears schematic

### Developer Experience
- **Hot Reload**: `/botanica reload` command reloads plant specs, soil specs, and schematics without restart
- **Real-Time UI**: Action bar displays plant/soil status when looking at blocks (water, nutrients, growth progress)
- **Template Generation**: Missing configs auto-generate with sensible defaults

---

## Architecture Overview

### Core Components

```
┌─────────────────────────────────────────────────────────────┐
│                    Player Interactions                      │
│  (ItemActions.json → ActionResolver → PlantActions)         │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ↓
┌─────────────────────────────────────────────────────────────┐
│                  Simulation Loop (1s tick)                  │
│  SimulationService: Resource consumption, growth logic      │
│  - Checks growth gates (water/nutrient ranges)              │
│  - Consumes resources (waterPerSec, nutrientsPerSec)        │
│  - Advances growth progress                                 │
│  - Updates visual stages (single blocks or schematics)      │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ↓
┌─────────────────────────────────────────────────────────────┐
│                      Mutation Bus                           │
│  Queues world changes: BlockMutation, ItemDropMutation,     │
│  SoundMutation, SchematicMutation                           │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ↓
┌─────────────────────────────────────────────────────────────┐
│                   Mutation Applier                          │
│  Applies queued changes to Minecraft world (rate-limited)   │
└─────────────────────────────────────────────────────────────┘
```

### Data Flow

1. **Initialization**:
   - `SimulationDataManager` loads plant specs, soil specs from JSON files
   - `SchematicManager` loads schematics on-demand and caches them
   - `ItemActionRegistry` loads item-to-action mappings

2. **Player Interaction**:
   - Player uses item → `PlayerInteractListener` → `ActionResolver`
   - `ActionResolver` validates context (block type, plant state, soil state)
   - Routes to appropriate `PlantActions` method (tillSoil, plantSeed, harvestPlant, etc.)
   - Changes queued to `MutationBus`

3. **Simulation Tick** (every 1 second):
   - `SimulationService.tickPlant()` for each plant
   - Check if resources are within growth gates
   - If yes: consume resources, advance growth progress
   - If growth stage changes: update visual (place/remove blocks or schematics)
   - Visual changes queued to `MutationBus`

4. **Mutation Application** (async, rate-limited):
   - `MutationApplier` processes queue
   - Applies block changes, spawns items, plays sounds
   - Respects `mutationCapPerTick` to prevent lag

### Repository Layer

- **PlantRepo**: In-memory storage of `PlantInstance` objects (UUID → PlantInstance)
- **SoilRepo**: In-memory storage of `SoilInstance` objects (BlockPos → SoilInstance)
- **Planned**: SQLite persistence for server restarts

---

## Soil System

### SoilInstance (Runtime State)
```java
{
    pos: BlockPos,           // World position
    soilType: "LOAMY",       // References SoilSpec.id
    water: 50.0,             // 0-100 resource level
    nutrients: 75.0,         // 0-100 resource level
    qualityMult: 1.2,        // Rolled on creation, affects growth
    lastUpdateAt: Instant    // Last simulation tick
}
```

### SoilSpec (Configuration)
```json
{
    "id": "LOAMY",
    "displayName": "Loamy soil",
    "block": "DIRT"
}
```

### Soil Lifecycle
1. **Creation**: Player uses hoe on block → `PlantActions.hoeSoil()`
2. **Quality Roll**: Random multiplier (e.g., 0.8-1.5) applied to plant growth rates
3. **Resource Management**: Players add/remove water and nutrients via item interactions
4. **Plant Linking**: Plants store reference to soil position for resource lookup

---

## Plant System

### PlantInstance (Runtime State)
```java
{
    id: UUID,                    // Unique plant identifier
    speciesId: "oak_tree",       // References PlantSpec.id
    pos: BlockPos,               // Plant root position
    soilPos: BlockPos,           // Linked soil position
    progress: 0.65,              // Growth progress (0.0-1.0)
    complete: false,             // Harvest-ready flag
    currentSchematicId: "oak_tree_full",  // Active schematic (if any)
    fertility: 2.5,              // Bonus added to drop ranges
    rotation: 90,                // Y-axis rotation (0, 90, 180, 270)
    lastSimAt: Instant,          // Last simulation tick
    nextUpdateAt: Instant        // Next scheduled update
}
```

### PlantSpec (Configuration)
Key fields:
- `id`, `name`, `kind`: Identification and categorization
- `baseGrowthDuration`: Seconds to complete one growth stage
- `optimalSoil`: Soil type ID for 1.5x growth bonus
- `destroyOnHarvest`: If false, plant regresses to 2nd-to-last stage (renewable)
- `allowRotation`: If true, plant randomly rotates on planting
- `growthFactors`: Resource requirements and consumption rates
  - `waterMin/Max`, `nutMin/Max`: Growth gate ranges (ideal conditions, 1.0x growth speed)
  - `waterPerSec`, `nutrientsPerSec`: Consumption rates while growing
  - `robustness`: Buffer range outside ideal gates that allows 0.75x growth speed
- `mutations.stages`: Array of visual stages (block IDs or `"schematic:id"`)
- `drops`: Array of drop configurations with ranges, height, and grouping

### Plant Lifecycle

1. **Planting**: Player uses seed item on soil → `PlantActions.plantSeed()`
   - Creates `PlantInstance` with `progress = 0.0`
   - Links to soil at position below
   - Assigns random rotation if `allowRotation = true`

2. **Growth Simulation**: Every 1 second
   - Check if soil resources are within `waterMin-waterMax` and `nutMin-nutMax`
   - If yes: Consume `waterPerSec` and `nutrientsPerSec` from soil
   - Advance `progress` based on `baseGrowthDuration`, `qualityMult`, and optimal soil bonus
   - When `progress` crosses stage boundary: update visual (block or schematic)
   - When `progress >= 1.0`: Set `complete = true`

3. **Harvest**: Player uses tool on mature plant → `PlantActions.harvestPlant()`
   - Calculate drops: `adjustedMin/Max = drop.min/max + (fertility * lootLevel)`
   - Spawn drops at positions determined by `dropRangeMin/Max`, `dropHeight`, `makeGroups`
   - If `destroyOnHarvest = true`: Remove plant and schematic
   - If `destroyOnHarvest = false`: Regress to 2nd-to-last stage, resume growth

4. **Removal**: Player breaks non-whitelist block of plant → `PlantBreakListener`
   - Lookup plant via `SchematicBlockIndex`
   - Remove all schematic blocks (surgical removal)
   - Drop schematic-defined blocks as items
   - Remove `PlantInstance` from repo

---

## ItemActions as Simulation Entrypoints

`ItemActions.json` is the **bridge between Minecraft items and simulation logic**. It defines what happens when a player uses an item.

### Structure
```json
{
  "TILL_SOIL": [
    { "itemId": "WOODEN_HOE", "soilId": "LOAMY", "consume": false, "give": null }
  ],
  "PLANT": [
    { "itemId": "OAK_SAPLING", "plantId": "oak_tree", "consume": true, "give": null }
  ],
  "HARVEST_PLANT": [
    { "itemId": "WOODEN_HOE", "consume": false, "give": null }
  ],
  "ADD_WATER": [
    { "itemId": "WATER_BUCKET", "units": 50, "consume": true, "give": "BUCKET" }
  ],
  "ADD_NUTRIENTS": [
    { "itemId": "BONE_MEAL", "units": 30, "consume": true, "give": null }
  ]
}
```

### Execution Flow

1. **Event**: Player right-clicks with item → `PlayerInteractListener`
2. **Lookup**: `ItemActionRegistry.getActionForItem(itemId)`
3. **Context Validation**: `ActionResolver` checks if action is valid
   - TILL_SOIL: Target block must be valid, no soil already exists
   - PLANT: Must have soil at target, no plant already there
   - HARVEST: Must have complete plant at target
   - ADD_WATER/NUTRIENTS: Must have soil or plant at target
4. **Execution**: `ActionResolver` routes to `PlantActions` method
5. **Item Handling**: If `consume = true`, decrement stack; if `give` is set, add item to inventory
6. **Mutation**: Changes queued to `MutationBus`, applied asynchronously

### Action Types

- **TILL_SOIL**: Creates `SoilInstance`, rolls quality multiplier, replaces block
- **PLANT**: Creates `PlantInstance`, links to soil, places initial visual
- **HARVEST_PLANT**: Calculates drops, removes/regresses plant based on `destroyOnHarvest`
- **ADD_WATER / REMOVE_WATER**: Modifies `soil.water` by `units` (clamped 0-100)
- **ADD_NUTRIENTS / REMOVE_NUTRIENTS**: Modifies `soil.nutrients` by `units` (clamped 0-100)

---

## Summary

The Botanica simulation is a **layered, event-driven system** where:
- **Players** interact via items (entrypoints)
- **Simulation** ticks plants forward (growth logic)
- **Mutations** queue world changes (async application)
- **Data** drives everything (JSON specs for plants, soils, items, schematics)

This architecture provides:
- ✅ **Performance**: Async mutations, rate-limiting, spatial indexing
- ✅ **Flexibility**: Hot-reload, config-driven design
- ✅ **Extensibility**: Clean separation of concerns, plugin-friendly
- ✅ **Designer-Friendly**: All gameplay tuning in JSON, no Java required

