# How to Add a Plant Set

This guide walks through the steps to add new plants to Botanica, from creating the specification file to making seeds plantable in-game.

---

## Overview

Plants in Botanica are organized into **Plant Sets**. Each set is a JSON file containing:
- **Set metadata**: ID, reward points
- **Plant definitions**: One or more plant specifications

This structure allows you to bundle related plants together (e.g., "Fruit Trees", "Desert Plants", "Magic Herbs") while keeping configuration organized.

---

## Step-by-Step Guide

### 1. Create a Plant Set Spec File

Navigate to: `plugins/Botanica/sim/specs/plants/`

Create a new JSON file (e.g., `FruitTrees.json`):

```json
{
  "set": "FruitTrees",
  "reward": 100,
  "plants": [
    // Plant definitions go here (see Step 2)
  ]
}
```

**Fields:**
- `set`: Unique identifier for this plant set
- `reward`: Points awarded for completing the set (future feature)
- `plants`: Array of plant specifications

---

### 2. Define Plants in the Set

Each plant in the `plants` array requires these fields:

```json
{
  "id": "apple_tree",
  "name": "Apple Tree",
  "kind": "TREE",
  "baseGrowthDuration": 30,
  "optimalSoil": "LOAMY",
  "destroyOnHarvest": false,
  "allowRotation": false,
  "growthFactors": {
    "waterMin": 20,
    "waterMax": 80,
    "nutMin": 30,
    "nutMax": 70,
    "robustness": 10,
    "waterPerSec": 0.5,
    "nutrientsPerSec": 0.3
  },
  "mutations": {
    "stages": [
      "OAK_SAPLING",
      "schematic:apple_stump",
      "schematic:apple_tree_empty",
      "schematic:apple_tree_full"
    ]
  },
  "drops": [
    {
      "type": "FRUIT",
      "id": "APPLE",
      "min": 3,
      "max": 6,
      "dropHeight": 3.0,
      "dropRangeMin": 1.0,
      "dropRangeMax": 1.5,
      "makeGroups": true
    }
  ]
}
```

#### Key Fields Explained

**Identity:**
- `id`: Unique plant identifier (referenced in ItemActions.json)
- `name`: Display name shown to players
- `kind`: Plant category ("TREE", "GENERIC", "FLOWER", "VINE")

**Growth:**
- `baseGrowthDuration`: Seconds to complete full growth (0-100%, modified by soil quality and optimal soil bonus)
- `optimalSoil`: Soil type ID for 1.5x growth speed
- `destroyOnHarvest`: If `false`, plant regresses to 2nd-to-last stage instead of being removed (renewable harvest)
- `allowRotation`: If `true`, plant randomly rotates 0°/90°/180°/270° on planting

**Growth Factors:**
- `waterMin/Max`: Water range (0-100) for ideal growth (1.0x speed)
- `nutMin/Max`: Nutrient range (0-100) for ideal growth (1.0x speed)
- `waterPerSec`, `nutrientsPerSec`: Resource consumption rates while growing
- `robustness`: Buffer range outside ideal gates that still allows 0.75x growth speed

**Mutations (Visuals):**
- `stages`: Array of visual stages as plant grows
  - Can be Minecraft block IDs: `"WHEAT"`, `"OAK_SAPLING"`
  - Can be schematics: `"schematic:oak_tree_full"` (see [Create Schematics](CreateSchematics.md))
  - First stage = seed/start, last stage = mature/complete
  - Plant visual updates automatically as growth progresses through stages

**Drops:**
- `type`: Drop category (future feature, use "FRUIT" for now)
- `id`: Minecraft item ID to drop
- `min`, `max`: Drop quantity range (modified by plant fertility)
- `dropHeight`: Y-offset from plant root for spawning items
- `dropRangeMin/Max`: Distance from plant center for drop spawning (blocks)
- `makeGroups`: If `true`, splits drops into 2-3 groups at different positions

---

### 3. Understanding `mutations.stages` 🔥

The `stages` array defines how your plant looks as it grows. This is one of the most important parts of plant configuration.

**How Stages Work:**
- Plants progress from 0% to 100% growth
- Stages are **evenly distributed** across the growth range
- Example with 4 stages: Stage 0 (0-25%), Stage 1 (25-50%), Stage 2 (50-75%), Stage 3 (75-100%)

**Single Block Stages:**
```json
"stages": [
  "WHEAT",           // Stage 0: Seed
  "CARROTS",         // Stage 1: Young crop
  "BEETROOTS",       // Stage 2: Mature crop
  "POTATOES"         // Stage 3: Ready to harvest
]
```
- Use Minecraft block IDs
- Each stage is a single block at the plant's root position

**Schematic Stages:**
```json
"stages": [
  "OAK_SAPLING",                    // Stage 0: Sapling (single block)
  "schematic:oak_stump",            // Stage 1: Small tree (multi-block)
  "schematic:oak_tree_empty",       // Stage 2: Full tree, no fruit
  "schematic:oak_tree_full"         // Stage 3: Full tree with fruit
]
```
- Prefix schematic IDs with `"schematic:"`
- Schematic name must match a file in `plugins/Botanica/sim/schematics/`
- You can mix single blocks and schematics

**Important Notes:**
- Renewable harvest (`destroyOnHarvest: false`) **requires at least 2 stages**
- When harvested, plant regresses to the **2nd-to-last stage** and resumes growth
- Breaking any non-whitelisted block in a schematic removes the entire plant

**Need multi-block plants?** See **[Create Schematics](CreateSchematics.md)** for detailed instructions on creating 3D plant structures.

---

### 4. Add Seed Item to ItemActions.json

Once your plant spec is defined, players need a way to plant it. This is done via `ItemActions.json`.

Navigate to: `plugins/Botanica/sim/ItemActions.json`

Add an entry under the `"PLANT"` action:

```json
{
  "PLANT": [
    {
      "itemId": "OAK_SAPLING",
      "plantId": "apple_tree",
      "consume": true,
      "give": null
    }
  ]
}
```

**Fields:**
- `itemId`: Minecraft item ID that will plant the seed (e.g., `"OAK_SAPLING"`, `"WHEAT_SEEDS"`)
- `plantId`: Must match the `id` field in your plant spec
- `consume`: If `true`, the seed item is consumed on planting
- `give`: Item to give back after planting (usually `null` for seeds)

**Example from Template:**
```json
{
  "itemId": "OAK_SAPLING",
  "plantId": "oak_tree",
  "consume": true,
  "give": null
}
```

When a player right-clicks soil with an `OAK_SAPLING`, it creates an `oak_tree` plant and consumes the sapling.

---

## Complete Example

Here's the full setup for adding an apple tree to the game:

### 1. Plant Set File: `FruitTrees.json`

```json
{
  "set": "FruitTrees",
  "reward": 100,
  "plants": [
    {
      "id": "apple_tree",
      "name": "Apple Tree",
      "kind": "TREE",
      "baseGrowthDuration": 30,
      "optimalSoil": "LOAMY",
      "destroyOnHarvest": false,
      "allowRotation": false,
      "growthFactors": {
        "waterMin": 20,
        "waterMax": 80,
        "nutMin": 30,
        "nutMax": 70,
        "robustness": 10,
        "waterPerSec": 0.5,
        "nutrientsPerSec": 0.3
      },
      "mutations": {
        "stages": [
          "OAK_SAPLING",
          "schematic:apple_stump",
          "schematic:apple_tree_empty",
          "schematic:apple_tree_full"
        ]
      },
      "drops": [
        {
          "type": "FRUIT",
          "id": "APPLE",
          "min": 3,
          "max": 6,
          "dropHeight": 3.0,
          "dropRangeMin": 1.0,
          "dropRangeMax": 1.5,
          "makeGroups": true
        }
      ]
    }
  ]
}
```

### 2. ItemActions Entry

```json
{
  "PLANT": [
    {
      "itemId": "APPLE",
      "plantId": "apple_tree",
      "consume": true,
      "give": null
    }
  ]
}
```

### 3. Create Schematics

You'll need to create the schematic files referenced in `mutations.stages`:
- `apple_stump.json`
- `apple_tree_empty.json`
- `apple_tree_full.json`

See **[Create Schematics](CreateSchematics.md)** for instructions.

---

## Testing Your Plant

1. **Reload the plugin**: `/botanica reload` (or restart server)
2. **Create soil**: Use a hoe on dirt/grass
3. **Plant the seed**: Right-click soil with your seed item (e.g., `OAK_SAPLING`)
4. **Add resources**: Use water buckets and bone meal to keep water/nutrients in range
5. **Wait for growth**: Plant will progress through stages automatically
6. **Harvest**: Right-click mature plant with a hoe

---

## Troubleshooting

**Plant won't grow:**
- Check that soil water/nutrients are within `waterMin/Max` and `nutMin/Max`
- Use `/botanica status` while looking at the plant to see current values
- Check server logs for errors

**Can't plant seed:**
- Verify `plantId` in `ItemActions.json` matches `id` in plant spec
- Ensure you're clicking on soil (not air or regular blocks)
- Check that soil exists at the target position

**Schematic not appearing:**
- Verify schematic file exists in `plugins/Botanica/sim/schematics/`
- Check that schematic `id` in JSON matches the filename (without `.json`)
- Check server logs for schematic loading errors

**Renewable harvest not working:**
- Ensure `destroyOnHarvest: false` is set
- Verify plant has **at least 2 stages** in `mutations.stages`

---

## Next Steps

- **Add more plants to your set**: Just add more objects to the `plants` array
- **Create multi-block plants**: See [Create Schematics](CreateSchematics.md)
- **Tune growth rates**: Adjust `baseGrowthDuration`, `waterPerSec`, `nutrientsPerSec`
- **Add harvest tools**: Add entries to `HARVEST_PLANT` in `ItemActions.json`
- **Experiment with drops**: Try different `dropRangeMin/Max` and `makeGroups` settings

