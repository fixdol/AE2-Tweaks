package com.fxd927.ae2tweaks.mixin;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.ItemUpgradesChanged;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.upgrades.Upgrades;
import appeng.items.tools.powered.AbstractPortableCell;
import appeng.items.tools.powered.PoweredContainerItem;
import appeng.parts.automation.FormationPlanePart;
import com.fxd927.ae2tweaks.AE2TweaksConfig;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.DoubleSupplier;

@Mixin(AbstractPortableCell.class)
public abstract class MixinAbstractPortableCell extends PoweredContainerItem {
    public MixinAbstractPortableCell(DoubleSupplier powerCapacity, Properties props) {
        super(powerCapacity, props);
    }

    @Redirect(method = "getUpgrades", at = @At(value = "INVOKE", target = "Lappeng/api/upgrades/UpgradeInventories;forItem(Lnet/minecraft/world/item/ItemStack;ILappeng/api/upgrades/ItemUpgradesChanged;)Lappeng/api/upgrades/IUpgradeInventory;"))
    public IUpgradeInventory getUpgrades(ItemStack stack, int maxUpgrades, ItemUpgradesChanged changeCallback) {
        int maxUpgradeSlots = AE2TweaksConfig.PORTABLE_CELL_MAX_UPGRADES.get();
        return UpgradeInventories.forItem(stack, maxUpgradeSlots, this::onUpgradesChanged);
    }

    @Shadow
    public void onUpgradesChanged(ItemStack stack, IUpgradeInventory upgrades) {
        setAEMaxPowerMultiplier(stack, 1 + Upgrades.getEnergyCardMultiplier(upgrades));
    }
}
