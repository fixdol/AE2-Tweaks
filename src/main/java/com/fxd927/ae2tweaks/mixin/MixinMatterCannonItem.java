package com.fxd927.ae2tweaks.mixin;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.ItemUpgradesChanged;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.upgrades.Upgrades;
import appeng.items.tools.powered.MatterCannonItem;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.items.tools.powered.powersink.AEBasePoweredItem;
import com.fxd927.ae2tweaks.AE2TweaksConfig;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.DoubleSupplier;

@Mixin(MatterCannonItem.class)
public class MixinMatterCannonItem extends AEBasePoweredItem {
    @Unique
    int maxUpgradeSlots = AE2TweaksConfig.WIRELESS_TERMINAL_MAX_UPGRADES.get();

    public MixinMatterCannonItem(DoubleSupplier powerCapacity, Properties props) {
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
        return 800d + 800d * Upgrades.getEnergyCardMultiplier(getUpgrades(stack, maxUpgradeSlots , this::onUpgradesChanged));
    }
}
