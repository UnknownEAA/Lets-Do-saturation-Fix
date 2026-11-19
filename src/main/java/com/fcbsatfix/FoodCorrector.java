package com.fcbsatfix;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import org.slf4j.Logger;

/**
 * Corrects the saturation of Farm & Charm and Brewery foods.
 *
 * <p>The bug: on NeoForge, both mods re-apply their food config and store the config's
 * saturation <i>factor</i> (for example 0.6) as the food's <i>final</i> saturation. Since
 * Minecraft 1.20.5, the final value should be {@code hunger x factor x 2} (that is what the
 * vanilla food builder and both mods' own config comments use).
 *
 * <p>What this class does: for each food item of those two mods, it asks the mod for its
 * configured factor. If the item's saturation still equals that raw factor (the bug's
 * signature), it replaces it with {@code hunger x factor x 2}. Everything else about the food
 * (hunger, effects, eating speed, bowl returned) is copied unchanged.
 *
 * <p>Self-disabling: once the bug is fixed upstream, the saturation no longer equals the raw
 * factor, so nothing is changed. If the mods' internals change and the factor can't be read,
 * nothing is changed either; a warning is written to the log.
 */
public final class FoodCorrector {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float EPSILON = 0.001f;

    /** Mod id -> the class in that mod that reads its food config. */
    private static final String[][] TARGETS = {
            {"farm_and_charm", "net.satisfy.farm_and_charm.platform.neoforge.PlatformHelperImpl"},
            {"brewery", "net.satisfy.brewery.platform.neoforge.PlatformHelperImpl"},
    };

    /** Items whose saturation this mod corrected. Used by the placed-meal bite fix. */
    private static final Set<Item> CORRECTED = ConcurrentHashMap.newKeySet();

    private FoodCorrector() {}

    public static boolean wasCorrected(Item item) {
        return CORRECTED.contains(item);
    }

    static void onModifyDefaultComponents(ModifyDefaultComponentsEvent event) {
        CORRECTED.clear();
        for (String[] target : TARGETS) {
            String modId = target[0];
            if (!ModList.get().isLoaded(modId)) {
                continue;
            }
            Method factorReader = findFactorReader(modId, target[1]);
            if (factorReader == null) {
                continue;
            }
            int fixed = 0;
            for (Item item : BuiltInRegistries.ITEM) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                if (modId.equals(id.getNamespace()) && correct(event, item, id, factorReader)) {
                    fixed++;
                }
            }
            if (fixed > 0) {
                LOGGER.info("[{}] Corrected saturation of {} {} foods",
                        SaturationFix.MOD_ID, fixed, modId);
            } else {
                LOGGER.info("[{}] No {} foods needed correcting (already fixed upstream?)",
                        SaturationFix.MOD_ID, modId);
            }
        }
    }

    private static boolean correct(ModifyDefaultComponentsEvent event, Item item, ResourceLocation id,
                                   Method factorReader) {
        // NeoForge applies each modify() immediately, so this is the value after the mod's own patch.
        FoodProperties current = item.components().get(DataComponents.FOOD);
        if (current == null || current.nutrition() <= 0) {
            return false;
        }
        float factor = readFactor(factorReader, id.getPath());
        if (!(factor > 0f)) {
            return false; // not one of the config-driven foods
        }
        if (Math.abs(current.saturation() - factor) > EPSILON) {
            return false; // not the bug's signature: leave it alone
        }
        float expected = current.nutrition() * factor * 2f;
        if (expected <= current.saturation() + EPSILON) {
            return false;
        }
        FoodProperties fixed = new FoodProperties(
                current.nutrition(),
                expected,
                current.canAlwaysEat(),
                current.eatSeconds(),
                current.usingConvertsTo(),
                current.effects());
        event.modify(item, patch -> patch.set(DataComponents.FOOD, fixed));
        CORRECTED.add(item);
        return true;
    }

    private static Method findFactorReader(String modId, String className) {
        try {
            Method method = Class.forName(className).getMethod("getSaturation", String.class);
            if (method.getReturnType() == float.class) {
                return method;
            }
            LOGGER.warn("[{}] {} changed: getSaturation no longer returns a float. Skipping {}.",
                    SaturationFix.MOD_ID, className, modId);
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.warn("[{}] Could not find {}'s food config reader ({}). Skipping {}.",
                    SaturationFix.MOD_ID, modId, e.toString(), modId);
        }
        return null;
    }

    private static float readFactor(Method factorReader, String key) {
        try {
            return (Float) factorReader.invoke(null, key);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0f;
        }
    }
}
