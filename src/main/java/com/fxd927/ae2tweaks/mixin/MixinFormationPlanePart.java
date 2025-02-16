package com.fxd927.ae2tweaks.mixin;

import appeng.menu.implementations.FormationPlaneMenu;
import appeng.parts.automation.FormationPlanePart;
import appeng.parts.storagebus.StorageBusPart;
import com.fxd927.ae2tweaks.AE2TweaksConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FormationPlanePart.class)
public class MixinFormationPlanePart {
    @Inject(method = "getUpgradeSlots", at = @At("RETURN"), cancellable = true)
    protected void getUpgradeSlots(CallbackInfoReturnable<Integer> cir) {
        int maxUpgradeSlots = AE2TweaksConfig.STORAGE_BUS_MAX_UPGRADES.get();
        cir.setReturnValue(maxUpgradeSlots);
    }
}
