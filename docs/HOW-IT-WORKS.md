# How it works (plain language)

## The bug

In Minecraft 1.21.1, every food stores two numbers: **hunger** (nutrition) and **saturation**.
Before Minecraft 1.20.5, mods stored a *saturation multiplier* instead, and the game worked out
`saturation = hunger × multiplier × 2`. Since 1.20.5, the final saturation is stored directly.

Farm & Charm and Brewery have config options like `oatPancakeSaturation = 0.6`. Brewery's own
config comment says it means "hunger × factor × 2". When the game starts on NeoForge, both mods
re-apply these config values to their foods. In that step they store the multiplier (0.6) as the
final saturation, skipping "× hunger × 2".

So an Oat Pancake (hunger 5, multiplier 0.6) gets saturation **0.6** instead of **6.0**. Config
values are capped at 2.0, so editing the config can't make up the difference.

## The fix

Right after Farm & Charm and Brewery apply their values, this mod:

1. Looks at every food item from those two mods.
2. Asks the mod for that food's configured multiplier, through the same method the mod uses
   itself, so your config edits are respected.
3. If the food's saturation still equals that raw multiplier (the bug's fingerprint), replaces it
   with `hunger × multiplier × 2`. Everything else about the food is copied unchanged.
4. Otherwise leaves the food alone.

Because of step 3, the mod does nothing once the bug is fixed upstream: the stored value will no
longer equal the raw multiplier.

### Placed meals
Some meals can be placed and eaten in bites. That code path in Let's Do's shared library treats
the stored saturation as a multiplier. With the buggy value this accidentally came out right;
with the corrected value it would give too much. A small mixin converts the value back for the
foods this mod corrected, and only those, so bites give the same saturation as eating the item.
If that library changes, the mixin simply does nothing rather than crashing.

## Why this is not a copy of Farm & Charm or Brewery

- It contains **no code, textures, models or data** from either mod. It is three small Java files
  (around 200 lines in total) written from claude.
- It adds no items, blocks or recipes. It only corrects one number on existing foods.
- It reads each mod's config value by calling the mod's own public method at runtime. Nothing
  from the mods is bundled or copied.
- Both mods are **required dependencies**: the game won't start without them, and CurseForge
  installs them alongside this mod.

## Files

| File | Purpose |
|---|---|
| `src/main/java/com/fcbsatfix/SaturationFix.java` | Mod entry point; registers the fix to run last |
| `src/main/java/com/fcbsatfix/FoodCorrector.java` | Detects and corrects the affected foods |
| `src/main/java/com/fcbsatfix/mixin/FoodBlockBiteMixin.java` | Keeps placed-meal bites correct |
| `src/main/resources/META-INF/neoforge.mods.toml` | Mod info and required dependencies |
| `src/main/resources/fcb_saturation_fix.mixins.json` | Registers the mixin |
| `build.gradle`, `settings.gradle`, `gradle.properties`, `gradlew*`, `gradle/` | Build setup (NeoForge's standard ModDevGradle) |
| `.github/workflows/build.yml` | Tells GitHub how to build the jar automatically |
| `docs/github-build-workflow.yml` | Spare copy of the above, in case the `.github` folder goes missing |
