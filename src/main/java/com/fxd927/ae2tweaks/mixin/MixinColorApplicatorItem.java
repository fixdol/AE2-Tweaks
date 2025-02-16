package com.fxd927.ae2tweaks.mixin;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.ItemUpgradesChanged;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.upgrades.Upgrades;
import appeng.items.AEBaseItem;
import appeng.items.tools.powered.AbstractPortableCell;
import appeng.items.tools.powered.ColorApplicatorItem;
import appeng.items.tools.powered.powersink.AEBasePoweredItem;
import com.fxd927.ae2tweaks.AE2TweaksConfig;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.DoubleSupplier;

@Mixin(ColorApplicatorItem.class)
public class MixinColorApplicatorItem extends AEBasePoweredItem {
    int maxUpgradeSlots = AE2TweaksConfig.COLOR_APPLICATOR_MAX_UPGRADES.get();

    public MixinColorApplicatorItem(DoubleSupplier powerCapacity, Properties props) {
        super(powerCapacity, props);
    }

    @Redirect(method = "getUpgrades", at = @At(value = "INVOKE", target = "Lappeng/api/upgrades/UpgradeInventories;forItem(Lnet/minecraft/world/item/ItemStack;ILappeng/api/upgrades/ItemUpgradesChanged;)Lappeng/api/upgrades/IUpgradeInventory;"))
    public IUpgradeInventory getUpgrades(ItemStack stack, int maxUpgrades, ItemUpgradesChanged changeCallback) {
        return UpgradeInventories.forItem(stack, maxUpgradeSlots, this::onUpgradesChanged);
    }

    @Shadow
    private void onUpgradesChanged(ItemStack stack, IUpgradeInventory upgrades) {
        setAEMaxPowerMultiplier(stack, 1 + Upgrades.getEnergyCardMultiplier(upgrades) * 8);
    }

    @Shadow
    @Override
    public double getChargeRate(ItemStack stack) {
        return 80d + 80d * Upgrades.getEnergyCardMultiplier(getUpgrades(stack, maxUpgradeSlots, this::onUpgradesChanged));
    }
}
