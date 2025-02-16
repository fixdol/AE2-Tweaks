package com.fxd927.ae2tweaks.mixin;

import appeng.api.upgrades.Upgrades;
import net.minecraft.world.level.ItemLike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Upgrades.class)
public class MixinUpgrades {
    @Inject(method = "getMaxInstallable", at = @At("HEAD"), cancellable = true)
    private static void overrideMaxInstallable(ItemLike card, ItemLike upgradableItem, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(Integer.MAX_VALUE);
    }
}
