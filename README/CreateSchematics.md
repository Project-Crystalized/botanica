# How to Make Schematics

Schematics allow you to create multi-block plant structures in Botanica. A schematic defines a 3D shape made of multiple blocks, centered around a root position.

---

## Overview

Schematic files are JSON files that define:
- **Block positions**: Relative coordinates from the plant's root
- **Block types**: Minecraft block IDs for each position

When a plant grows to a stage that uses a schematic, the simulation:
1. Places all blocks defined in the schematic (replacing any blocks in the way)
2. Registers all blocks to the plant's spatial index for fast lookup
3. Optionally rotates the schematic based on the plant's rotation (if `allowRotation: true`)

When the plant is removed or regresses:
1. Removes only the blocks defined in the schematic (surgical removal)
2. Does not remove blocks from other sources (player-placed, natural terrain, etc.)

---

## File Structure

Schematic files are located in: `plugins/Botanica/sim/schematics/`

**Naming Convention:**
- Filename must match the schematic ID
- Example: `oak_tree_full.json` → ID: `"oak_tree_full"`

**Basic Structure:**

```json
{
  "id": "oak_tree_full",
  "blocks": [
    {"x": 0, "y": 0, "z": 0, "material": "OAK_LOG"},
    {"x": 0, "y": 1, "z": 0, "material": "OAK_LOG"},
    {"x": 0, "y": 2, "z": 0, "material": "OAK_LOG"}
  ]
}
```

---

## Required Fields

### Root Level

- **`id`** (string, required): Unique identifier for the schematic
  - Must match the filename (without `.json`)
  - Referenced in plant specs as `"schematic:oak_tree_full"`

- **`blocks`** (array, required): List of blocks that make up the schematic
  - Each block is an object with `x`, `y`, `z`, `material` fields

### Block Fields

Each block in the `blocks` array requires:

- **`x`** (integer): X-offset from plant root position
  - Positive = east, Negative = west

- **`y`** (integer): Y-offset from plant root position
  - Must be 0 or positive (plants only grow upwards)
  - Starts at 0 (plant root level)
  - Negative values are not allowed to prevent soil replacement and ground holes

- **`z`** (integer): Z-offset from plant root position
  - Positive = south, Negative = north

- **`material`** (string): Minecraft block ID
  - Examples: `"OAK_LOG"`, `"OAK_LEAVES"`, `"APPLE"`
  - Must be a valid Minecraft block type

---

## Coordinate System

The schematic coordinate system is **relative** to the plant's root position:

```
       North (z-)
           |
    West---+---East (x+)
    (x-)   |   
           |
       South (z+)

    Up (y+) / Down (y-)
```

**Example:**
If a plant is at world position `(100, 64, 200)` and a schematic block is at `(1, 2, -1)`:
- World position = `(101, 66, 199)`

---

## Example Schematics

### Small Tree Stump

A simple 3-block tall trunk:

```json
{
  "id": "oak_stump",
  "blocks": [
    {"x": 0, "y": 0, "z": 0, "material": "OAK_LOG"},
    {"x": 0, "y": 1, "z": 0, "material": "OAK_LOG"},
    {"x": 0, "y": 2, "z": 0, "material": "OAK_LOG"}
  ]
}
```

**Visual:**
```
     y=2  [LOG]
     y=1  [LOG]
     y=0  [LOG]  ← root
```

---

### Full Oak Tree

A more complex tree with trunk, branches, and leaves:

```json
{
  "id": "oak_tree_full",
  "blocks": [
    {"x": 0, "y": 0, "z": 0, "material": "OAK_LOG"},
    {"x": 0, "y": 1, "z": 0, "material": "OAK_LOG"},
    {"x": 0, "y": 2, "z": 0, "material": "OAK_LOG"},
    {"x": 0, "y": 3, "z": 0, "material": "OAK_LOG"},
    
    {"x": 1, "y": 3, "z": 0, "material": "OAK_LEAVES"},
    {"x": -1, "y": 3, "z": 0, "material": "OAK_LEAVES"},
    {"x": 0, "y": 3, "z": 1, "material": "OAK_LEAVES"},
    {"x": 0, "y": 3, "z": -1, "material": "OAK_LEAVES"},
    
    {"x": 0, "y": 4, "z": 0, "material": "OAK_LEAVES"},
    {"x": 1, "y": 4, "z": 0, "material": "OAK_LEAVES"},
    {"x": -1, "y": 4, "z": 0, "material": "OAK_LEAVES"},
    {"x": 0, "y": 4, "z": 1, "material": "OAK_LEAVES"},
    {"x": 0, "y": 4, "z": -1, "material": "OAK_LEAVES"}
  ]
}
```

**Visual (side view):**
```
     y=4    [LEAVES] [LEAVES] [LEAVES]
     y=3    [LEAVES]  [LOG]  [LEAVES]
     y=2              [LOG]
     y=1              [LOG]
     y=0              [LOG]  ← root
```

**Visual (top view at y=3):**
```
             [LEAVES]
    [LEAVES]  [LOG]  [LEAVES]
             [LEAVES]
```

---

### Cactus Tower

A vertical cactus with horizontal arms:

```json
{
  "id": "cactus_large",
  "blocks": [
    {"x": 0, "y": 0, "z": 0, "material": "CACTUS"},
    {"x": 0, "y": 1, "z": 0, "material": "CACTUS"},
    {"x": 0, "y": 2, "z": 0, "material": "CACTUS"},
    
    {"x": 1, "y": 2, "z": 0, "material": "CACTUS"},
    {"x": -1, "y": 2, "z": 0, "material": "CACTUS"},
    {"x": 0, "y": 2, "z": 1, "material": "CACTUS"},
    {"x": 0, "y": 2, "z": -1, "material": "CACTUS"},
    
    {"x": 0, "y": 3, "z": 0, "material": "CACTUS"}
  ]
}
```

**Visual:**
```
     y=3              [CACTUS]
     y=2    [CACTUS] [CACTUS] [CACTUS]
     y=1              [CACTUS]
     y=0              [CACTUS]  ← root
```

---

## Using Schematics in Plant Specs

Once you've created a schematic file, reference it in your plant's `mutations.stages`:

```json
{
  "id": "oak_tree",
  "mutations": {
    "stages": [
      "OAK_SAPLING",                    // Stage 0: Single block
      "schematic:oak_stump",            // Stage 1: Small tree
      "schematic:oak_tree_empty",       // Stage 2: Full tree
      "schematic:oak_tree_full"         // Stage 3: Tree with fruit
    ]
  }
}
```

**Important:** Use the `"schematic:"` prefix when referencing schematics in plant specs.

---

## Growth and Removal Behavior

### Growth (Reckless)
When a plant grows to a schematic stage:
- **All blocks in the schematic are placed**, replacing any existing blocks (terrain, player-placed, etc.)
- This is intentional: plants grow "recklessly" and overtake their space

### Removal (Surgical)
When a plant is removed or regresses:
- **Only blocks defined in the schematic are removed**
- Blocks not in the schematic (e.g., natural terrain underneath) remain untouched
- Removed blocks drop as items for player collection

**Example:**
- Player places a stone block inside a tree schematic
- Tree regresses/is removed
- Stone block remains (not part of schematic definition)
- Only tree blocks (logs, leaves) are removed

---

## Rotation Support

If a plant has `allowRotation: true`, schematics automatically rotate around the Y-axis:

- **0°**: No rotation (original coordinates)
- **90°**: Rotated clockwise (east)
- **180°**: Rotated 180° (south)
- **270°**: Rotated counter-clockwise (west)

**Rotation Transform:**
- `(x, z)` at 0° → `(-z, x)` at 90° → `(-x, -z)` at 180° → `(z, -x)` at 270°
- Y-coordinate (height) is **never affected** by rotation

This allows visual variety without creating separate schematic files for each direction.

---

## Best Practices

### 1. Start with Small Schematics
- Begin with 3-5 blocks to test the system
- Add complexity once you understand the coordinate system

### 2. Use Consistent Naming
- Name schematics descriptively: `oak_tree_small`, `oak_tree_large`, `apple_tree_fruiting`
- Include size or state in the name

### 3. Test Rotation
- If using `allowRotation: true`, test all 4 rotations
- Ensure schematic looks good from all angles

### 4. Keep Root at Ground Level
- Use `y: 0` as ground level (plant root)
- Positive Y values go up (use for trunk, branches, leaves)
- Negative Y values are not allowed

### 5. Mind the Block Limit
- No hard limit on schematic size, but be reasonable
- Very large schematics (>100 blocks) may cause brief lag spikes during placement

### 6. Use Stages Wisely
- Create progression: small → medium → large
- Each stage should be visibly different
- For renewable harvest, ensure 2nd-to-last stage is meaningful (not just a sapling). Consider using matching block positions for both the last and 2nd-to-last stages, using different leaf block types where one (2nd-to-last) is just leaves, while the final stage (harvestable) is leaves with the respective fruits.

---

## Troubleshooting

**Schematic not loading:**
- Check that `id` in JSON matches filename (case-sensitive)
- Verify JSON syntax (use a JSON validator)
- Check server logs for loading errors

**Blocks appearing in wrong positions:**
- Double-check coordinate signs (positive vs. negative)
- Remember: X+ = east, Z+ = south, Y+ = up

**Schematic overlapping with terrain:**
- This is expected (reckless growth)
- Players can place blocks back after plant is removed
- Consider adjusting schematic size or placement

**Rotation looks wrong:**
- Test with symmetrical schematics first
- Remember only X/Z rotate, not Y
- Asymmetric schematics may look odd at certain rotations

---

## Tools for Building Schematics

While there's no in-game schematic builder yet, you can:

1. **Build in creative mode**, note coordinates, transcribe to JSON
3. **Hand-write JSON** for simple structures (recommended for learning)
4. **Future**: A schematic builder plugin/utility is planned

---

## Next Steps

- **Experiment with shapes**: Trees, cacti, mushrooms, crystals
- **Add blocks**: Use custom blocks for visual interest
- **Create progression**: Small → medium → large stages
- **Test renewable harvest**: Ensure 2nd-to-last stage is a good "empty" state

