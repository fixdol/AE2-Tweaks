package com.fxd927.ae2tweaks.mixin;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.MachineUpgradesChanged;
import appeng.api.upgrades.UpgradeInventories;
import appeng.blockentity.misc.InscriberBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.fxd927.ae2tweaks.AE2TweaksConfig;
import net.minecraft.world.level.ItemLike;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InscriberBlockEntity.class)
public abstract class MixinInscriberBlockEntity {
    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/upgrades/UpgradeInventories;forMachine(Lnet/minecraft/world/level/ItemLike;ILappeng/api/upgrades/MachineUpgradesChanged;)Lappeng/api/upgrades/IUpgradeInventory;"
            )
    )
    private IUpgradeInventory modifyUpgradeSlots(ItemLike machineType, int maxUpgrades, MachineUpgradesChanged changeCallback) {
        int maxUpgradeSlots = AE2TweaksConfig.INSCRIBER_MAX_UPGRADES.get();
        return UpgradeInventories.forMachine(AEBlocks.INSCRIBER, maxUpgradeSlots, changeCallback);
    }
}
