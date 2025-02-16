package com.fxd927.ae2tweaks.mixin;

import appeng.api.implementations.menuobjects.IMenuItem;
import appeng.api.upgrades.*;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.items.tools.powered.PoweredContainerItem;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.MenuOpener;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.menu.me.common.MEStorageMenu;
import com.fxd927.ae2tweaks.AE2TweaksConfig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.DoubleSupplier;

@Mixin(WirelessTerminalItem.class)
public class MixinWirelessTerminalItem extends PoweredContainerItem implements IMenuItem, IUpgradeableItem {
    public MixinWirelessTerminalItem(DoubleSupplier powerCapacity, Properties props) {
        super(powerCapacity, props);
    }

    @Redirect(method = "getUpgrades", at = @At(value = "INVOKE", target = "Lappeng/api/upgrades/UpgradeInventories;forItem(Lnet/minecraft/world/item/ItemStack;ILappeng/api/upgrades/ItemUpgradesChanged;)Lappeng/api/upgrades/IUpgradeInventory;"))
    public IUpgradeInventory getUpgrades(ItemStack stack, int maxUpgrades, ItemUpgradesChanged changeCallback) {
        int maxUpgradeSlots = AE2TweaksConfig.WIRELESS_TERMINAL_MAX_UPGRADES.get();
        return UpgradeInventories.forItem(stack, maxUpgradeSlots, this::onUpgradesChanged);
    }

    @Shadow
    private void onUpgradesChanged(ItemStack stack, IUpgradeInventory upgrades) {
        setAEMaxPowerMultiplier(stack, 1 + Upgrades.getEnergyCardMultiplier(upgrades));
    }

    @Shadow
    @Override
    public double getChargeRate(ItemStack stack) {
        return 800d + 800d * Upgrades.getEnergyCardMultiplier(getUpgrades(stack));
    }


    @Shadow
    @Nullable
    @Override
    public WirelessTerminalMenuHost<?> getMenuHost(Player player, ItemMenuHostLocator locator, @Nullable BlockHitResult hitResult) {
        return new WirelessTerminalMenuHost<>((WirelessTerminalItem) (Object) this, player, locator, (p, subMenu) -> openFromInventory(p, locator, true));
    }

    @Shadow
    protected boolean openFromInventory(Player player, ItemMenuHostLocator locator, boolean returningFromSubmenu) {
        var is = locator.locateItem(player);

        if (!player.level().isClientSide() && checkPreconditions(is)) {
            return MenuOpener.open(getMenuType(), player, locator, returningFromSubmenu);
        }
        return false;
    }

    @Shadow
    public MenuType<?> getMenuType() {
        return MEStorageMenu.WIRELESS_TYPE;
    }

    @Shadow
    protected boolean checkPreconditions(ItemStack item) {
        return !item.isEmpty() && item.getItem() == this;
    }
}
