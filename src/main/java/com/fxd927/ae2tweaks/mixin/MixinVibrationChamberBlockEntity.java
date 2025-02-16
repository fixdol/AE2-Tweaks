package com.fxd927.ae2tweaks.mixin;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.MachineUpgradesChanged;
import appeng.api.upgrades.UpgradeInventories;
import appeng.blockentity.misc.VibrationChamberBlockEntity;
import appeng.core.definitions.AEBlocks;
import com.fxd927.ae2tweaks.AE2TweaksConfig;
import net.minecraft.world.level.ItemLike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(VibrationChamberBlockEntity.class)
public class MixinVibrationChamberBlockEntity {
    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/upgrades/UpgradeInventories;forMachine(Lnet/minecraft/world/level/ItemLike;ILappeng/api/upgrades/MachineUpgradesChanged;)Lappeng/api/upgrades/IUpgradeInventory;"
            )
    )
    private IUpgradeInventory modifyUpgradeSlots(ItemLike machineType, int maxUpgrades, MachineUpgradesChanged changeCallback) {
        int maxUpgradeSlots = AE2TweaksConfig.VIBRATION_CHAMBER_MAX_UPGRADES.get();
        return UpgradeInventories.forMachine(AEBlocks.VIBRATION_CHAMBER, maxUpgradeSlots, changeCallback);
    }
}
