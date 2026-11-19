package com.fcbsatfix.mixin;

import com.fcbsatfix.FoodCorrector;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Keeps placed meals (eaten in bites from a block) correct after the item fix.
 *
 * <p>The shared library's FoodBlock passes an item's <i>final</i> saturation to
 * {@code FoodData.eat(int, float)}, which in 1.21.1 expects a <i>factor</i> and multiplies by
 * hunger x 2 again. With the buggy item values that accidentally came out right; with the
 * corrected values it would give too much. For the items this mod corrected (and only those),
 * this converts the value back to a factor so each bite gives exactly the corrected saturation.
 *
 * <p>{@code require = 0}: if the library changes, this simply does nothing instead of crashing.
 */
@Mixin(targets = "net.satisfy.foundation.food.FoodBlock", remap = false)
public abstract class FoodBlockBiteMixin {

    @ModifyArg(
            method = "tryEat",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/food/FoodData;eat(IF)V"),
            index = 1,
            require = 0)
    private float fcbsatfix$biteSaturation(int nutrition, float saturation) {
        Item item = ((ItemLike) (Object) this).asItem();
        if (nutrition > 0 && FoodCorrector.wasCorrected(item)) {
            return saturation / (2f * nutrition);
        }
        return saturation;
    }
}
