package net.crownsheep.ender_relay.block.entity;

import net.crownsheep.ender_relay.block.custom.EnderRelayBlock;
import net.crownsheep.ender_relay.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EnderRelayBlockEntity extends BlockEntity {
    private BlockPos lodestonePosition;

    private final ItemStackHandler itemHandler = new ItemStackHandler(1) {
        @Override
        protected int getStackLimit(int slot, @NotNull ItemStack stack) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (level == null)
                return false;
            return (EnderRelayBlock.isEnd(level) ? (getLodestonePosition() != null || EnderRelayBlock.lodestoneExists(level, getBlockPos())) : true) && stack.is(Items.END_CRYSTAL);
        }

        @Override
        protected void onContentsChanged(int slot) {
            if (level != null) {
                setChanged();
            }
        }
    };

    @Override
    public void setChanged() {
        super.setChanged();
        if(level != null) {
            if (hasCrystal()){
                EnderRelayBlock.setChargedBlockstate(level, getBlockState(), getBlockPos(), null, true);
                level.playSound(null, (double) getBlockPos().getX() + 0.5D, (double) getBlockPos().getY() + 0.5D, (double) getBlockPos().getZ() + 0.5D, ModSounds.ENDER_RELAY_CHARGE.get(), SoundSource.PLAYERS, 1.0F, 0.9F + level.random.nextFloat() * 0.2F);
            } else {
                EnderRelayBlock.setChargedBlockstate(level, getBlockState(), getBlockPos(), null, false);
            }
        }
    }

    private LazyOptional<IItemHandler> lazyItemHandler = LazyOptional.empty();

    public EnderRelayBlockEntity(BlockPos pPos, BlockState pBlockState) {
        super(ModBlockEntities.ENDER_RELAY_BLOCK_ENTITY.get(), pPos, pBlockState);
    }

    public boolean hasNoLodestonePosition() {
        return getLodestonePosition() == null;
    }

    public BlockPos getLodestonePosition() {
        return lodestonePosition;
    }

    public void setLodestonePosition(@Nullable BlockPos blockPos) {
        this.lodestonePosition = blockPos;
    }

    public void addCrystal() {
        itemHandler.insertItem(0, new ItemStack(Items.END_CRYSTAL), false);
    }

    public boolean hasCrystal() {
        return !itemHandler.getStackInSlot(0).isEmpty();
    }

    public void removeCrystal() {
        itemHandler.extractItem(0, 1, false);
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return lazyItemHandler.cast();
        }

        return super.getCapability(cap, side);
    }


    @Override
    public void load(CompoundTag nbt) {
        if (nbt.contains("Inventory")) itemHandler.deserializeNBT(nbt.getCompound("Inventory"));
        if (nbt.contains("LodestonePos") && !NbtUtils.readBlockPos(nbt.getCompound("LodestonePos")).equals(BlockPos.ZERO)) lodestonePosition = NbtUtils.readBlockPos(nbt.getCompound("LodestonePos"));
        super.load(nbt);
    }

    @Override
    protected void saveAdditional(CompoundTag pTag) {
        super.saveAdditional(pTag);
        pTag.put("Inventory", itemHandler.serializeNBT());
        if (lodestonePosition != null) pTag.put("LodestonePos", NbtUtils.writeBlockPos(lodestonePosition));
    }

    @Override
    public void onLoad() {
        super.onLoad();
        lazyItemHandler = LazyOptional.of(() -> itemHandler);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        lazyItemHandler.invalidate();
    }
}