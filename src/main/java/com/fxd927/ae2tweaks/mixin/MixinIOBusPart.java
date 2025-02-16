package com.fxd927.ae2tweaks.mixin;

import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.core.definitions.AEItems;
import appeng.parts.automation.IOBusPart;
import appeng.parts.automation.UpgradeablePart;
import com.fxd927.ae2tweaks.AE2TweaksConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(IOBusPart.class)
public abstract class MixinIOBusPart extends UpgradeablePart {
    public MixinIOBusPart(IPartItem<?> partItem) {
        super(partItem);
    }

    @Inject(method = "getUpgradeSlots", at = @At("RETURN"), cancellable = true)
    protected void getUpgradeSlots(CallbackInfoReturnable<Integer> cir) {
        int maxUpgradeSlots = AE2TweaksConfig.IO_BUS_MAX_UPGRADES.get();
        cir.setReturnValue(maxUpgradeSlots);
    }

    /**
     * @author FixedDolphin927
     * @reason To Add New Cases
     */
    @Overwrite
    protected int getOperationsPerTick() {
        return switch (getInstalledUpgrades(AEItems.SPEED_CARD)) {
            default -> 1;
            case 1 -> 8;
            case 2 -> 32;
            case 3 -> 64;
            case 4 -> 96;
            case 5 -> 128;
            case 6 -> 160;
            case 7 -> 192;
            case 8 -> 224;
        };
    }
}
