package com.fxd927.ae2tweaks.mixin;

import appeng.api.config.*;
import appeng.api.implementations.blockentities.ICrankable;
import appeng.api.inventories.ISegmentedInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.RelativeSide;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.MachineUpgradesChanged;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.util.AECableType;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.blockentity.grid.AENetworkedPoweredBlockEntity;
import appeng.blockentity.misc.InscriberBlockEntity;
import appeng.blockentity.misc.InscriberRecipes;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.settings.TickRates;
import appeng.recipes.handlers.InscriberProcessType;
import appeng.recipes.handlers.InscriberRecipe;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.CombinedInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;
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
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

@Mixin(InscriberBlockEntity.class)
public  class MixinInscriberBlockEntity extends AENetworkedPoweredBlockEntity
        implements IGridTickable, IUpgradeableObject, IConfigurableObject {
    private static final int MAX_PROCESSING_STEPS = 200;

    @Final
    @Shadow
    private IUpgradeInventory upgrades;
    @Final
    @Shadow
    private  IConfigManager configManager;
    @Shadow
    private int processingTime = 0;
    // cycles from 0 - 16, at 8 it preforms the action, at 16 it re-enables the
    // normal routine.
    @Shadow
    private boolean smash;
    /**
     * Purely visual on the client-side.
     */
    @Shadow
    private boolean repeatSmash;
    @Shadow
    private int finalStep;
    @Shadow
    private long clientStart;

    // Internally visible inventories

    @Final
    @Shadow
    private IAEItemFilter baseFilter;
    @Shadow
    private final AppEngInternalInventory topItemHandler = new AppEngInternalInventory(this, 1, 64, baseFilter);
    @Shadow
    private final AppEngInternalInventory bottomItemHandler = new AppEngInternalInventory(this, 1, 64, baseFilter);
    @Shadow
    private final AppEngInternalInventory sideItemHandler = new AppEngInternalInventory(this, 2, 64, baseFilter);
    // Combined internally visible inventories
    @Shadow
    private final InternalInventory inv = new CombinedInternalInventory(this.topItemHandler,
            this.bottomItemHandler, this.sideItemHandler);

    // "Hack" to see if active recipe changed.
    @Shadow
    private final Map<InternalInventory, ItemStack> lastStacks = new IdentityHashMap<>(Map.of(
            topItemHandler, ItemStack.EMPTY, bottomItemHandler, ItemStack.EMPTY,
            sideItemHandler, ItemStack.EMPTY));

    // The externally visible inventories (with filters applied)
    @Final
    @Shadow
    private InternalInventory topItemHandlerExtern;
    @Final
    @Shadow
    private InternalInventory bottomItemHandlerExtern;
    @Final
    @Shadow
    private InternalInventory sideItemHandlerExtern;
    // Combined externally visible inventories
    @Final
    @Shadow
    private InternalInventory combinedItemHandlerExtern;

    @Shadow
    private InscriberRecipe cachedTask = null;

    public MixinInscriberBlockEntity(BlockEntityType<?> blockEntityType, BlockPos pos, BlockState blockState) {
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
        int maxUpgradeSlots = AE2TweaksConfig.INSCRIBER_MAX_UPGRADES.get();
        return UpgradeInventories.forMachine(AEBlocks.INSCRIBER, maxUpgradeSlots, changeCallback);
    }

    @Shadow
    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.COVERED;
    }

    @Shadow
    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        this.upgrades.writeToNBT(data, "upgrades", registries);
        this.configManager.writeToNBT(data, registries);
    }

    @Shadow
    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        this.upgrades.readFromNBT(data, "upgrades", registries);
        this.configManager.readFromNBT(data, registries);
        // TODO 1.22: Remove compat with old format.
        if ("NO".equals(data.getString("inscriber_buffer_size"))) {
            this.configManager.putSetting(Settings.INSCRIBER_INPUT_CAPACITY, InscriberInputCapacity.FOUR);
        }

        // Update stack tracker
        lastStacks.put(topItemHandler, topItemHandler.getStackInSlot(0));
        lastStacks.put(bottomItemHandler, bottomItemHandler.getStackInSlot(0));
        lastStacks.put(sideItemHandler, sideItemHandler.getStackInSlot(0));
    }

    @Shadow
    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        var c = super.readFromStream(data);

        var oldSmash = isSmash();
        var newSmash = data.readBoolean();

        if (oldSmash != newSmash && newSmash) {
            setSmash(true);
        }

        for (int i = 0; i < this.inv.size(); i++) {
            this.inv.setItemDirect(i, ItemStack.OPTIONAL_STREAM_CODEC.decode(data));
        }
        this.cachedTask = null;

        return c;
    }

    @Shadow
    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);

        data.writeBoolean(isSmash());
        for (int i = 0; i < this.inv.size(); i++) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(data, inv.getStackInSlot(i));
        }
    }

    @Shadow
    @Override
    protected void saveVisualState(CompoundTag data) {
        super.saveVisualState(data);

        data.putBoolean("smash", isSmash());
    }

    @Shadow
    @Override
    protected void loadVisualState(CompoundTag data) {
        super.loadVisualState(data);

        setSmash(data.getBoolean("smash"));
    }

    @Shadow
    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.complementOf(EnumSet.of(orientation.getSide(RelativeSide.FRONT)));
    }

    @Shadow
    @Override
    protected void onOrientationChanged(BlockOrientation orientation) {
        super.onOrientationChanged(orientation);

        this.setPowerSides(getGridConnectableSides(orientation));
    }

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

    @Shadow
    @Override
    public InternalInventory getInternalInventory() {
        return this.inv;
    }

    @Shadow
    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        if (slot == 0) {
            boolean sameItemSameTags = ItemStack.isSameItemSameComponents(inv.getStackInSlot(0), lastStacks.get(inv));
            lastStacks.put(inv, inv.getStackInSlot(0).copy());
            if (sameItemSameTags) {
                return; // Don't care if it's just a count change
            }

            // Reset recipe
            this.setProcessingTime(0);
            this.cachedTask = null;
        }

        // Update displayed stacks on the client
        if (!this.isSmash()) {
            this.markForUpdate();
        }

        getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
    }

    @Shadow
    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(TickRates.Inscriber, !hasAutoExportWork() && !this.hasCraftWork());
    }

    @Shadow
    private boolean hasAutoExportWork() {
        return !this.sideItemHandler.getStackInSlot(1).isEmpty()
                && configManager.getSetting(Settings.AUTO_EXPORT) == YesNo.YES;
    }

    @Shadow
    private boolean hasCraftWork() {
        var task = this.getTask();
        if (task != null) {
            // Only process if the result would fit.
            return sideItemHandler.insertItem(1, task.getResultItem().copy(), true).isEmpty();
        }

        this.setProcessingTime(0);
        return this.isSmash();
    }

    @Shadow
    @Nullable
    public InscriberRecipe getTask() {
        if (this.cachedTask == null && level != null) {
            ItemStack input = this.sideItemHandler.getStackInSlot(0);
            ItemStack plateA = this.topItemHandler.getStackInSlot(0);
            ItemStack plateB = this.bottomItemHandler.getStackInSlot(0);
            if (input.isEmpty()) {
                return null; // No input to handle
            }

            this.cachedTask = InscriberRecipes.findRecipe(level, input, plateA, plateB, true);
        }
        return this.cachedTask;
    }

    /**
     * @author FixedDolphin927
     * @reason To Add New Cases
     */
    @Overwrite
    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (this.isSmash()) {
            this.finalStep++;
            if (this.finalStep == 8) {
                final InscriberRecipe out = this.getTask();
                if (out != null) {
                    final ItemStack outputCopy = out.getResultItem().copy();

                    if (this.sideItemHandler.insertItem(1, outputCopy, false).isEmpty()) {
                        this.setProcessingTime(0);
                        if (out.getProcessType() == InscriberProcessType.PRESS) {
                            this.topItemHandler.extractItem(0, 1, false);
                            this.bottomItemHandler.extractItem(0, 1, false);
                        }
                        this.sideItemHandler.extractItem(0, 1, false);
                    }
                }
                this.saveChanges();
            } else if (this.finalStep == 16) {
                this.finalStep = 0;
                this.setSmash(false);
                this.markForUpdate();
            }
        } else if (this.hasCraftWork()) {
            getMainNode().ifPresent(grid -> {
                IEnergyService eg = grid.getEnergyService();
                IEnergySource src = this;

                // Note: required ticks = 16 + ceil(MAX_PROCESSING_STEPS / speedFactor)
                final int speedFactor = switch (this.upgrades.getInstalledUpgrades(AEItems.SPEED_CARD)) {
                    default -> 2; // 116 ticks
                    case 1 -> 3; // 83 ticks
                    case 2 -> 5; // 56 ticks
                    case 3 -> 10; // 36 ticks
                    case 4 -> 50; // 20 ticks
                    case 5 -> 70;
                    case 6 -> 100;
                    case 7 -> 130;
                    case 8 -> 160;
                };
                final int powerConsumption = 10 * speedFactor;
                final double powerThreshold = powerConsumption - 0.01;
                double powerReq = this.extractAEPower(powerConsumption, Actionable.SIMULATE, PowerMultiplier.CONFIG);

                if (powerReq <= powerThreshold) {
                    src = eg;
                    powerReq = eg.extractAEPower(powerConsumption, Actionable.SIMULATE, PowerMultiplier.CONFIG);
                }

                if (powerReq > powerThreshold) {
                    src.extractAEPower(powerConsumption, Actionable.MODULATE, PowerMultiplier.CONFIG);
                    this.setProcessingTime(this.getProcessingTime() + speedFactor);
                }
            });

            if (this.getProcessingTime() > this.getMaxProcessingTime()) {
                this.setProcessingTime(this.getMaxProcessingTime());
                final InscriberRecipe out = this.getTask();
                if (out != null) {
                    final ItemStack outputCopy = out.getResultItem().copy();
                    if (this.sideItemHandler.insertItem(1, outputCopy, true).isEmpty()) {
                        this.setSmash(true);
                        this.finalStep = 0;
                        this.markForUpdate();
                    }
                }
            }
        }

        if (this.pushOutResult()) {
            return TickRateModulation.URGENT;
        }

        return this.hasCraftWork() ? TickRateModulation.URGENT
                : this.hasAutoExportWork() ? TickRateModulation.SLOWER : TickRateModulation.SLEEP;
    }

    /**
     * @return true if something was pushed, false otherwise
     */
    private boolean pushOutResult() {
        if (!this.hasAutoExportWork()) {
            return false;
        }

        var pushSides = EnumSet.allOf(Direction.class);
        if (isSeparateSides()) {
            pushSides.remove(this.getTop());
            pushSides.remove(this.getTop().getOpposite());
        }

        for (var dir : pushSides) {
            var target = InternalInventory.wrapExternal(level, getBlockPos().relative(dir), dir.getOpposite());

            if (target != null) {
                int startItems = this.sideItemHandler.getStackInSlot(1).getCount();
                this.sideItemHandler.insertItem(1, target.addItems(this.sideItemHandler.extractItem(1, 64, false)),
                        false);
                int endItems = this.sideItemHandler.getStackInSlot(1).getCount();

                if (startItems != endItems) {
                    return true;
                }
            }
        }

        return false;
    }

    @Nullable
    @Override
    public InternalInventory getSubInventory(ResourceLocation id) {
        if (id.equals(ISegmentedInventory.STORAGE)) {
            return this.getInternalInventory();
        } else if (id.equals(ISegmentedInventory.UPGRADES)) {
            return this.upgrades;
        }

        return super.getSubInventory(id);
    }

    private boolean isSeparateSides() {
        return this.configManager.getSetting(Settings.INSCRIBER_SEPARATE_SIDES) == YesNo.YES;
    }

    @Override
    protected InternalInventory getExposedInventoryForSide(Direction facing) {
        if (isSeparateSides()) {
            if (facing == this.getTop()) {
                return this.topItemHandlerExtern;
            } else if (facing == this.getTop().getOpposite()) {
                return this.bottomItemHandlerExtern;
            } else {
                return this.sideItemHandlerExtern;
            }
        } else {
            return this.combinedItemHandlerExtern;
        }
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return upgrades;
    }

    @Override
    public IConfigManager getConfigManager() {
        return configManager;
    }

    private void onConfigChanged(IConfigManager manager, Setting<?> setting) {
        if (setting == Settings.AUTO_EXPORT) {
            getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
        }

        if (setting == Settings.INSCRIBER_SEPARATE_SIDES) {
            // Our exposed inventory changed, invalidate caps!
            invalidateCapabilities();
        }

        if (setting == Settings.INSCRIBER_INPUT_CAPACITY) {
            var capacity = configManager.getSetting(Settings.INSCRIBER_INPUT_CAPACITY).capacity;
            topItemHandler.setMaxStackSize(0, capacity);
            sideItemHandler.setMaxStackSize(0, capacity);
            bottomItemHandler.setMaxStackSize(0, capacity);
        }

        saveChanges();
    }

    public long getClientStart() {
        return this.clientStart;
    }

    private void setClientStart(long clientStart) {
        this.clientStart = clientStart;
    }

    public boolean isSmash() {
        return this.smash;
    }

    public void setSmash(boolean smash) {
        if (smash && !this.smash) {
            setClientStart(System.currentTimeMillis());
        }
        this.smash = smash;
    }

    public boolean isRepeatSmash() {
        return repeatSmash;
    }

    public void setRepeatSmash(boolean repeatSmash) {
        this.repeatSmash = repeatSmash;
    }

    public int getMaxProcessingTime() {
        return this.MAX_PROCESSING_STEPS;
    }

    public int getProcessingTime() {
        return this.processingTime;
    }

    private void setProcessingTime(int processingTime) {
        this.processingTime = processingTime;
    }

    /**
     * Allow cranking from any side other than the front.
     */
    @Nullable
    public ICrankable getCrankable(Direction direction) {
        if (direction != getFront()) {
            return new Crankable();
        }
        return null;
    }
}
