package com.fxd927.ae2tweaks.mixin;

import appeng.api.config.*;
import appeng.api.inventories.ISegmentedInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageCells;
import appeng.api.storage.StorageHelper;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.StorageCell;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.MachineUpgradesChanged;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.util.AECableType;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.blockentity.grid.AENetworkedInvBlockEntity;
import appeng.blockentity.storage.IOPortBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.settings.TickRates;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.CombinedInternalInventory;
import appeng.util.inv.FilteredInternalInventory;
import appeng.util.inv.filter.AEItemFilters;
import com.fxd927.ae2tweaks.AE2TweaksConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(IOPortBlockEntity.class)
public class MixinIOPortBlockEntity extends AENetworkedInvBlockEntity
        implements IUpgradeableObject, IConfigurableObject, IGridTickable {
    @Unique
    private static final int maxUpgradeSlots = AE2TweaksConfig.IO_PORT_MAX_UPGRADES.get();

    @Shadow
    private static final int NUMBER_OF_CELL_SLOTS = 6;
    @Shadow
    private static final int NUMBER_OF_UPGRADE_SLOTS = maxUpgradeSlots;

    @Final
    @Shadow
    private IConfigManager manager;

    @Shadow
    private final AppEngInternalInventory inputCells = new AppEngInternalInventory(this, NUMBER_OF_CELL_SLOTS);
    @Shadow
    private final AppEngInternalInventory outputCells = new AppEngInternalInventory(this, NUMBER_OF_CELL_SLOTS);
    @Shadow
    private final InternalInventory combinedInventory = new CombinedInternalInventory(this.inputCells,
            this.outputCells);

    @Shadow
    private final InternalInventory inputCellsExt = new FilteredInternalInventory(this.inputCells,
            AEItemFilters.INSERT_ONLY);
    @Shadow
    private final InternalInventory outputCellsExt = new FilteredInternalInventory(this.outputCells,
            AEItemFilters.EXTRACT_ONLY);

    @Final
    @Shadow
    private IUpgradeInventory upgrades;
    @Final
    @Shadow
    private IActionSource mySrc;
    @Shadow
    private YesNo lastRedstoneState;

    @Shadow
    private boolean isActive = false;

    public MixinIOPortBlockEntity(BlockEntityType<?> blockEntityType, BlockPos pos, BlockState blockState) {
        super(blockEntityType, pos, blockState);
    }

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/upgrades/UpgradeInventories;forMachine(Lnet/minecraft/world/level/ItemLike;ILappeng/api/upgrades/MachineUpgradesChanged;)Lappeng/api/upgrades/IUpgradeInventory;"
            )
    )
    private IUpgradeInventory modifyUpgradeSlots(ItemLike machineType, int maxUpgrades, MachineUpgradesChanged changeCallback) {
        return UpgradeInventories.forMachine(AEBlocks.IO_PORT, NUMBER_OF_UPGRADE_SLOTS, changeCallback);
    }

    @Shadow
    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        this.manager.writeToNBT(data, registries);
        this.upgrades.writeToNBT(data, "upgrades", registries);
        data.putInt("lastRedstoneState", this.lastRedstoneState.ordinal());
    }

    @Shadow
    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        this.manager.readFromNBT(data, registries);
        this.upgrades.readFromNBT(data, "upgrades", registries);
        if (data.contains("lastRedstoneState")) {
            this.lastRedstoneState = YesNo.values()[data.getInt("lastRedstoneState")];
        }
    }

    @Shadow
    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeBoolean(this.isActive());
    }

    @Shadow
    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean ret = super.readFromStream(data);

        final boolean isActive = data.readBoolean();
        ret = isActive != this.isActive || ret;
        this.isActive = isActive;

        return ret;
    }

    @Shadow
    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.SMART;
    }

    private void updateTask() {
        getMainNode().ifPresent((grid, node) -> {
            if (this.hasWork()) {
                grid.getTickManager().wakeDevice(node);
            } else {
                grid.getTickManager().sleepDevice(node);
            }
        });
    }

    @Shadow
    public void updateRedstoneState() {
        final YesNo currentState = this.level.getBestNeighborSignal(this.worldPosition) != 0 ? YesNo.YES : YesNo.NO;
        if (this.lastRedstoneState != currentState) {
            this.lastRedstoneState = currentState;
            this.updateTask();
        }
    }

    @Shadow
    private boolean getRedstoneState() {
        if (this.lastRedstoneState == YesNo.UNDECIDED) {
            this.updateRedstoneState();
        }

        return this.lastRedstoneState == YesNo.YES;
    }

    @Shadow
    private boolean isEnabled() {
        if (!upgrades.isInstalled(AEItems.REDSTONE_CARD)) {
            return true;
        }

        final RedstoneMode rs = this.manager.getSetting(Settings.REDSTONE_CONTROLLED);
        if (rs == RedstoneMode.HIGH_SIGNAL) {
            return this.getRedstoneState();
        }
        return !this.getRedstoneState();
    }

    @Shadow
    public boolean isActive() {
        if (level != null && !level.isClientSide) {
            return this.getMainNode().isOnline();
        } else {
            return this.isActive;
        }
    }

    @Shadow
    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        if (reason != IGridNodeListener.State.GRID_BOOT) {
            this.markForUpdate();
        }
    }

    @Shadow
    @Override
    public IConfigManager getConfigManager() {
        return this.manager;
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return this.upgrades;
    }

    @Shadow
    @Nullable
    @Override
    public InternalInventory getSubInventory(ResourceLocation id) {
        if (id.equals(ISegmentedInventory.UPGRADES)) {
            return this.upgrades;
        } else if (id.equals(ISegmentedInventory.CELLS)) {
            return this.combinedInventory;
        } else {
            return super.getSubInventory(id);
        }
    }

    @Shadow
    private boolean hasWork() {
        if (this.isEnabled()) {

            return !this.inputCells.isEmpty();
        }

        return false;
    }

    @Shadow
    @Override
    public InternalInventory getInternalInventory() {
        return this.combinedInventory;
    }

    @Shadow
    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        if (this.inputCells == inv) {
            this.updateTask();
        }
    }

    @Shadow
    @Override
    protected InternalInventory getExposedInventoryForSide(Direction facing) {
        if (facing == this.getTop() || facing == this.getTop().getOpposite()) {
            return this.inputCellsExt;
        } else {
            return this.outputCellsExt;
        }
    }

    @Shadow
    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(TickRates.IOPort, !this.hasWork());
    }

    /**
     * @author FixedDolphin927
     * @reason To Add New Cases
     */
    @Overwrite
    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (!this.getMainNode().isActive()) {
            return TickRateModulation.IDLE;
        }

        TickRateModulation ret = TickRateModulation.SLEEP;
        long itemsToMove = 256;

        switch (upgrades.getInstalledUpgrades(AEItems.SPEED_CARD)) {
            case 1 -> itemsToMove *= 2;
            case 2 -> itemsToMove *= 4;
            case 4 -> itemsToMove *= 8;
            case 5 -> itemsToMove *= 16;
            case 6 -> itemsToMove *= 32;
            case 7 -> itemsToMove *= 64;
            case 8 -> itemsToMove *= 128;
        }

        var grid = getMainNode().getGrid();
        if (grid == null) {
            return TickRateModulation.IDLE;
        }

        for (int x = 0; x < NUMBER_OF_CELL_SLOTS; x++) {
            var cell = this.inputCells.getStackInSlot(x);

            var cellInv = StorageCells.getCellInventory(cell, null);

            if (cellInv == null) {
                // This item is not a valid storage cell, try to move it to the output
                moveSlot(x);
                continue;
            }

            if (itemsToMove > 0) {
                itemsToMove = transferContents(grid, cellInv, itemsToMove);

                if (itemsToMove > 0) {
                    ret = TickRateModulation.IDLE;
                } else {
                    ret = TickRateModulation.URGENT;
                }
            }

            if (itemsToMove > 0 && matchesFullnessMode(cellInv) && this.moveSlot(x)) {
                ret = TickRateModulation.URGENT;
            }
        }

        return ret;
    }

    /**
     * Work is complete when the inventory has reached the desired end-state.
     */
    @Shadow
    public boolean matchesFullnessMode(StorageCell inv) {
        return switch (manager.getSetting(Settings.FULLNESS_MODE)) {
            // In this mode, work completes as soon as no more items are moved within one operation,
            // independent of the actual inventory state
            case HALF -> true;
            case EMPTY -> inv.getStatus() == CellState.EMPTY;
            case FULL -> inv.getStatus() == CellState.FULL;
        };
    }

    @Shadow
    private long transferContents(IGrid grid, StorageCell cellInv, long itemsToMove) {

        var networkInv = grid.getStorageService().getInventory();

        KeyCounter srcList;
        MEStorage src, destination;
        if (this.manager.getSetting(Settings.OPERATION_MODE) == OperationMode.EMPTY) {
            src = cellInv;
            srcList = cellInv.getAvailableStacks();
            destination = networkInv;
        } else {
            src = networkInv;
            srcList = grid.getStorageService().getCachedInventory();
            destination = cellInv;
        }

        var energy = grid.getEnergyService();
        boolean didStuff;

        do {
            didStuff = false;

            for (var srcEntry : srcList) {
                var totalStackSize = srcEntry.getLongValue();
                if (totalStackSize > 0) {
                    var what = srcEntry.getKey();
                    var possible = destination.insert(what, totalStackSize, Actionable.SIMULATE, this.mySrc);

                    if (possible > 0) {
                        possible = Math.min(possible, itemsToMove * what.getAmountPerOperation());

                        possible = src.extract(what, possible, Actionable.MODULATE, this.mySrc);
                        if (possible > 0) {
                            var inserted = StorageHelper.poweredInsert(energy, destination, what, possible, this.mySrc);

                            if (inserted < possible) {
                                src.insert(what, possible - inserted, Actionable.MODULATE, this.mySrc);
                            }

                            if (inserted > 0) {
                                itemsToMove -= Math.max(1, inserted / what.getAmountPerOperation());
                                didStuff = true;
                            }

                            break;
                        }
                    }
                }
            }
        } while (itemsToMove > 0 && didStuff);

        return itemsToMove;
    }

    @Shadow
    private boolean moveSlot(int x) {
        if (this.outputCells.addItems(this.inputCells.getStackInSlot(x)).isEmpty()) {
            this.inputCells.setItemDirect(x, ItemStack.EMPTY);
            return true;
        }
        return false;
    }

    /**
     * Adds the items in the upgrade slots to the drop list.
     *
     * @param level level
     * @param pos   pos of block entity
     * @param drops drops of block entity
     */
    @Shadow
    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);

        for (var upgrade : upgrades) {
            drops.add(upgrade);
        }
    }

    @Shadow
    @Override
    public void clearContent() {
        super.clearContent();
        upgrades.clear();
    }
}
