package com.fxd927.ae2tweaks.mixin;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.implementations.IPowerChannelState;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.ISegmentedInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.util.AECableType;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.blockentity.crafting.MolecularAssemblerBlockEntity;
import appeng.blockentity.grid.AENetworkedInvBlockEntity;
import appeng.client.render.crafting.AssemblerAnimationStatus;
import appeng.core.AELog;
import appeng.core.AppEng;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.localization.GuiText;
import appeng.core.localization.Tooltips;
import appeng.core.network.clientbound.AssemblerAnimationPacket;
import appeng.crafting.CraftingEvent;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.CombinedInternalInventory;
import com.fxd927.ae2tweaks.AE2TweaksConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(MolecularAssemblerBlockEntity.class)
public class MixinMolecularAssemblerBlockEntity extends AENetworkedInvBlockEntity
        implements IUpgradeableObject, IGridTickable, ICraftingMachine, IPowerChannelState {
    @Shadow
    public static final ResourceLocation INV_MAIN = AppEng.makeId("molecular_assembler");
    @Final
    @Shadow
    private CraftingContainer craftingInv;
    @Shadow
    private final AppEngInternalInventory gridInv = new AppEngInternalInventory(this, 9 + 1, 1);
    @Shadow
    private final AppEngInternalInventory patternInv = new AppEngInternalInventory(this, 1, 1);
    @Final
    @Shadow
    private InternalInventory gridInvExt;
    @Shadow
    private final InternalInventory internalInv = new CombinedInternalInventory(this.gridInv, this.patternInv);
    @Final
    @Shadow
    private IUpgradeInventory upgrades;
    @Shadow
    private boolean isPowered = false;
    @Shadow
    private Direction pushDirection = null;
    @Shadow
    private ItemStack myPattern = ItemStack.EMPTY;
    @Shadow
    private IMolecularAssemblerSupportedPattern myPlan = null;
    @Shadow
    private double progress = 0;
    @Shadow
    private boolean isAwake = false;
    @Shadow
    private boolean forcePlan = false;
    @Shadow
    private boolean reboot = true;

    @Shadow
    @OnlyIn(Dist.CLIENT)
    private AssemblerAnimationStatus animationStatus;

    public MixinMolecularAssemblerBlockEntity(BlockEntityType<?> blockEntityType, BlockPos pos, BlockState blockState) {
        super(blockEntityType, pos, blockState);
    }

    @Inject(method = "getUpgradeSlots", at = @At("RETURN"), cancellable = true)
    private void getUpgradeSlots(CallbackInfoReturnable<Integer> cir) {
        int maxUpgradeSlots = AE2TweaksConfig.MOLECULAR_ASSEMBLER_MAX_UPGRADES.get();
        cir.setReturnValue(maxUpgradeSlots);
    }

    @Shadow
    @Override
    public PatternContainerGroup getCraftingMachineInfo() {
        Component name;
        if (hasCustomName()) {
            name = getCustomName();
        } else {
            name = AEBlocks.MOLECULAR_ASSEMBLER.asItem().getDescription();
        }
        var icon = AEItemKey.of(AEBlocks.MOLECULAR_ASSEMBLER);

        // List installed upgrades as the tooltip to differentiate assemblers by upgrade count
        List<Component> tooltip;
        var accelerationCards = getInstalledUpgrades(AEItems.SPEED_CARD);
        if (accelerationCards == 0) {
            tooltip = List.of();
        } else {
            tooltip = List.of(
                    GuiText.CompatibleUpgrade.text(
                            Tooltips.of(AEItems.SPEED_CARD.asItem().getDescription()),
                            Tooltips.ofUnformattedNumber(accelerationCards)));
        }

        return new PatternContainerGroup(icon, name, tooltip);
    }

    @Shadow
    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] table,
                               Direction where) {
        if (this.myPattern.isEmpty()) {
            boolean isEmpty = this.gridInv.isEmpty() && this.patternInv.isEmpty();

            // Only accept our own crafting patterns!
            if (isEmpty && patternDetails instanceof IMolecularAssemblerSupportedPattern pattern) {
                // We only support fluid and item stacks

                this.forcePlan = true;
                this.myPlan = pattern;
                this.pushDirection = where;

                this.fillGrid(table, pattern);

                this.updateSleepiness();
                this.saveChanges();
                return true;
            }
        }
        return false;
    }

    @Shadow
    private void fillGrid(KeyCounter[] table, IMolecularAssemblerSupportedPattern adapter) {
        adapter.fillCraftingGrid(table, this.gridInv::setItemDirect);

        // Sanity check
        for (var list : table) {
            list.removeZeros();
            if (!list.isEmpty()) {
                throw new RuntimeException("Could not fill grid with some items, including " + list.iterator().next());
            }
        }
    }

    @Shadow
    private void updateSleepiness() {
        final boolean wasEnabled = this.isAwake;
        this.isAwake = this.myPlan != null && this.hasMats() || this.canPush();
        if (wasEnabled != this.isAwake) {
            getMainNode().ifPresent((grid, node) -> {
                if (this.isAwake) {
                    grid.getTickManager().wakeDevice(node);
                } else {
                    grid.getTickManager().sleepDevice(node);
                }
            });
        }
    }

    @Shadow
    private boolean canPush() {
        return !this.gridInv.getStackInSlot(9).isEmpty();
    }

    @Shadow
    private boolean hasMats() {
        if (this.myPlan == null) {
            return false;
        }

        for (int x = 0; x < this.craftingInv.getContainerSize(); x++) {
            this.craftingInv.setItem(x, this.gridInv.getStackInSlot(x));
        }

        return !this.myPlan.assemble(this.craftingInv.asCraftInput(), this.getLevel()).isEmpty();
    }

    @Shadow
    @Override
    public boolean acceptsPlans() {
        return this.patternInv.isEmpty();
    }

    @Shadow
    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        final boolean c = super.readFromStream(data);
        final boolean oldPower = this.isPowered;
        this.isPowered = data.readBoolean();
        return this.isPowered != oldPower || c;
    }

    @Shadow
    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeBoolean(this.isPowered);
    }

    @Shadow
    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        if (this.forcePlan) {
            // If the plan is null it means the pattern previously loaded from NBT hasn't been decoded yet
            var pattern = myPlan != null ? myPlan.getDefinition().toStack() : myPattern;
            if (!pattern.isEmpty()) {
                data.put("myPlan", pattern.save(registries));
                data.putInt("pushDirection", this.pushDirection.ordinal());
            }
        }

        this.upgrades.writeToNBT(data, "upgrades", registries);
    }

    @Shadow
    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);

        // Reset current state back to defaults
        this.forcePlan = false;
        this.myPattern = ItemStack.EMPTY;
        this.myPlan = null;

        if (data.contains("myPlan")) {
            var pattern = ItemStack.parseOptional(registries, data.getCompound("myPlan"));
            if (!pattern.isEmpty()) {
                this.forcePlan = true;
                this.myPattern = pattern;
                this.pushDirection = Direction.values()[data.getInt("pushDirection")];
            }
        }

        this.upgrades.readFromNBT(data, "upgrades", registries);
        this.recalculatePlan();
    }

    @Shadow
    private void recalculatePlan() {
        this.reboot = true;

        if (this.forcePlan) {
            // If we're in forced mode, and myPattern is not empty, but the plan is null,
            // this indicates that we received an encoded pattern from NBT data, but
            // didn't have a chance to decode it yet
            if (getLevel() != null && myPlan == null) {
                if (!myPattern.isEmpty()) {
                    if (PatternDetailsHelper.decodePattern(myPattern,
                            getLevel()) instanceof IMolecularAssemblerSupportedPattern supportedPlan) {
                        this.myPlan = supportedPlan;
                    }
                }

                // Reset myPattern, so it will accept another job once this one finishes
                this.myPattern = ItemStack.EMPTY;

                // If the plan is still null, reset back to non-forced mode
                if (myPlan == null) {
                    AELog.warn("Unable to restore auto-crafting pattern after load: %s", myPattern);
                    this.forcePlan = false;
                }
            }

            return;
        }

        final ItemStack is = this.patternInv.getStackInSlot(0);

        boolean reset = true;

        if (!is.isEmpty()) {
            if (ItemStack.isSameItemSameComponents(is, this.myPattern)) {
                reset = false;
            } else if (PatternDetailsHelper.decodePattern(is,
                    getLevel()) instanceof IMolecularAssemblerSupportedPattern supportedPattern) {
                reset = false;
                this.progress = 0;
                this.myPattern = is;
                this.myPlan = supportedPattern;
            }
        }

        if (reset) {
            this.progress = 0;
            this.forcePlan = false;
            this.myPlan = null;
            this.myPattern = ItemStack.EMPTY;
            this.pushDirection = null;
        }

        this.updateSleepiness();
    }

    @Shadow
    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.COVERED;
    }

    @Shadow
    @Override
    public InternalInventory getSubInventory(ResourceLocation id) {
        if (id.equals(ISegmentedInventory.UPGRADES)) {
            return this.upgrades;
        } else if (id.equals(INV_MAIN)) {
            return this.internalInv;
        }

        return super.getSubInventory(id);
    }

    @Shadow
    @Override
    public InternalInventory getInternalInventory() {
        return this.internalInv;
    }

    @Shadow
    @Override
    protected InternalInventory getExposedInventoryForSide(Direction side) {
        return this.gridInvExt;
    }

    @Shadow
    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        if (inv == this.gridInv || inv == this.patternInv) {
            this.recalculatePlan();
        }
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
    public TickingRequest getTickingRequest(IGridNode node) {
        this.recalculatePlan();
        this.updateSleepiness();
        return new TickingRequest(1, 1, !this.isAwake);
    }

    /**
     * @author FixedDolphin927
     * @reason To Add New Cases
     */
    @Overwrite
    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (!this.gridInv.getStackInSlot(9).isEmpty()) {
            this.pushOut(this.gridInv.getStackInSlot(9));

            // did it eject?
            if (this.gridInv.getStackInSlot(9).isEmpty()) {
                this.saveChanges();
            }

            this.ejectHeldItems();
            this.updateSleepiness();
            this.progress = 0;
            return this.isAwake ? TickRateModulation.IDLE : TickRateModulation.SLEEP;
        }

        if (this.myPlan == null) {
            this.updateSleepiness();
            return TickRateModulation.SLEEP;
        }

        if (this.reboot) {
            ticksSinceLastCall = 1;
        }

        if (!this.isAwake) {
            return TickRateModulation.SLEEP;
        }

        this.reboot = false;
        int speed = 10;
        switch (this.upgrades.getInstalledUpgrades(AEItems.SPEED_CARD)) {
            case 0 -> this.progress += this.userPower(ticksSinceLastCall, speed = 10, 1.0);
            case 1 -> this.progress += this.userPower(ticksSinceLastCall, speed = 13, 1.3);
            case 2 -> this.progress += this.userPower(ticksSinceLastCall, speed = 17, 1.7);
            case 3 -> this.progress += this.userPower(ticksSinceLastCall, speed = 20, 2.0);
            case 4 -> this.progress += this.userPower(ticksSinceLastCall, speed = 25, 2.5);
            case 5 -> this.progress += this.userPower(ticksSinceLastCall, speed = 50, 5.0);
            case 6 -> this.progress += this.userPower(ticksSinceLastCall, speed = 60, 6.0);
            case 7 -> this.progress += this.userPower(ticksSinceLastCall, speed = 75, 7.5);
            case 8 -> this.progress += this.userPower(ticksSinceLastCall, speed = 100, 10.0);
        }

        if (this.progress >= 100) {
            for (int x = 0; x < this.craftingInv.getContainerSize(); x++) {
                this.craftingInv.setItem(x, this.gridInv.getStackInSlot(x));
            }

            var positionedInput = craftingInv.asPositionedCraftInput();
            var craftinginput = positionedInput.input();

            this.progress = 0;
            final ItemStack output = this.myPlan.assemble(craftinginput, this.getLevel());
            if (!output.isEmpty()) {
                output.onCraftedBySystem(level);
                CraftingEvent.fireAutoCraftingEvent(getLevel(), this.myPlan, output, this.craftingInv);

                // pushOut might reset the plan back to null, so get the remaining items before
                var craftingRemainders = this.myPlan.getRemainingItems(craftinginput);

                this.pushOut(output.copy());

                int craftingInputLeft = positionedInput.left();
                int craftingInputTop = positionedInput.top();

                // Clear out the rows/cols that are in the margin
                for (int y = 0; y < craftingInv.getHeight(); y++) {
                    for (int x = 0; x < craftingInv.getWidth(); x++) {
                        if (y < craftingInputTop || x < craftingInputLeft) {
                            int idx = x + y * craftingInv.getWidth();
                            gridInv.setItemDirect(idx, ItemStack.EMPTY);
                        }
                    }
                }
                for (int y = 0; y < craftinginput.height(); y++) {
                    for (int x = 0; x < craftinginput.width(); x++) {
                        int idx = x + craftingInputLeft + (y + craftingInputTop) * craftingInv.getWidth();
                        gridInv.setItemDirect(idx, craftingRemainders.get(x + y * craftinginput.width()));
                    }
                }

                if (this.patternInv.isEmpty()) {
                    this.forcePlan = false;
                    this.myPlan = null;
                    this.pushDirection = null;
                }

                this.ejectHeldItems();

                var item = AEItemKey.of(output);
                if (item != null) {
                    PacketDistributor.sendToPlayersNear(node.getLevel(), null, worldPosition.getX(),
                            worldPosition.getY(),
                            worldPosition.getZ(), 32,
                            new AssemblerAnimationPacket(this.worldPosition, (byte) speed, item));
                }

                this.saveChanges();
                this.updateSleepiness();
                return this.isAwake ? TickRateModulation.IDLE : TickRateModulation.SLEEP;
            }
        }

        return TickRateModulation.FASTER;
    }

    @Shadow
    private void ejectHeldItems() {
        if (this.gridInv.getStackInSlot(9).isEmpty()) {
            for (int x = 0; x < 9; x++) {
                final ItemStack is = this.gridInv.getStackInSlot(x);
                if (!is.isEmpty()
                        && (this.myPlan == null || !this.myPlan.isItemValid(x, AEItemKey.of(is), this.level))) {
                    this.gridInv.setItemDirect(9, is);
                    this.gridInv.setItemDirect(x, ItemStack.EMPTY);
                    this.saveChanges();
                    return;
                }
            }
        }
    }

    @Shadow
    private int userPower(int ticksPassed, int bonusValue, double acceleratorTax) {
        var grid = getMainNode().getGrid();
        if (grid != null) {
            return (int) (grid.getEnergyService().extractAEPower(ticksPassed * bonusValue * acceleratorTax,
                    Actionable.MODULATE, PowerMultiplier.CONFIG) / acceleratorTax);
        } else {
            return 0;
        }
    }

    @Shadow
    private void pushOut(ItemStack output) {
        if (this.pushDirection == null) {
            for (Direction d : Direction.values()) {
                output = this.pushTo(output, d);
            }
        } else {
            output = this.pushTo(output, this.pushDirection);
        }

        if (output.isEmpty() && this.forcePlan) {
            this.forcePlan = false;
            this.recalculatePlan();
        }

        this.gridInv.setItemDirect(9, output);
    }

    @Shadow
    private ItemStack pushTo(ItemStack output, Direction d) {
        if (output.isEmpty()) {
            return output;
        }

        var adaptor = InternalInventory.wrapExternal(getLevel(), this.worldPosition.relative(d), d.getOpposite());
        if (adaptor == null) {
            return output;
        }

        final int size = output.getCount();
        output = adaptor.addItems(output);
        final int newSize = output.isEmpty() ? 0 : output.getCount();

        if (size != newSize) {
            this.saveChanges();
        }

        return output;
    }

    @Shadow
    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        if (reason != IGridNodeListener.State.GRID_BOOT) {
            boolean newState = false;

            var grid = getMainNode().getGrid();
            if (grid != null) {
                newState = this.getMainNode().isPowered() && grid.getEnergyService().extractAEPower(1,
                        Actionable.SIMULATE, PowerMultiplier.CONFIG) > 0.0001;
            }

            if (newState != this.isPowered) {
                this.isPowered = newState;
                this.markForUpdate();
            }
        }
    }

    @Shadow
    @Override
    public boolean isPowered() {
        return this.isPowered;
    }

    @Shadow
    @Override
    public boolean isActive() {
        return this.isPowered;
    }

    @Shadow
    @OnlyIn(Dist.CLIENT)
    public void setAnimationStatus(@Nullable AssemblerAnimationStatus status) {
        this.animationStatus = status;
    }

    @Shadow
    @OnlyIn(Dist.CLIENT)
    @Nullable
    public AssemblerAnimationStatus getAnimationStatus() {
        return this.animationStatus;
    }

    @Shadow
    @Override
    public IUpgradeInventory getUpgrades() {
        return upgrades;
    }
}
