package com.fcbsatfix;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;

/**
 * Entry point. NeoForge creates this class when the game starts.
 *
 * All it does is register one listener. The listener runs at LOWEST priority so that it
 * runs after Farm & Charm and Brewery have applied their own (buggy) food values.
 */
@Mod(SaturationFix.MOD_ID)
public final class SaturationFix {
    public static final String MOD_ID = "fcb_saturation_fix";

    public SaturationFix(IEventBus modEventBus) {
        modEventBus.addListener(EventPriority.LOWEST, false, ModifyDefaultComponentsEvent.class,
                FoodCorrector::onModifyDefaultComponents);
    }
}
