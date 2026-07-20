package dev.crownsheep.ender_relay.block.entity;

import dev.crownsheep.ender_relay.block.custom.EnderRelayBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.ContainerSingleItem;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

import static dev.crownsheep.ender_relay.block.custom.EnderRelayBlock.*;

public class EnderRelayBlockEntity extends BlockEntity implements ContainerSingleItem {
    public static final Item FUEL_ITEM = Items.END_CRYSTAL;
    public static final String LODESTONE_POS_TAG = "LodestonePos";
    public static final String ITEM_TAG = "Item";

    private BlockPos lodestonePos;
    private final NonNullList<ItemStack> items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);

    public EnderRelayBlockEntity(BlockPos pPos, BlockState pBlockState) {
        super(ModBlockEntities.ENDER_RELAY_BLOCK_ENTITY.get(), pPos, pBlockState);
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        if (nbt.contains(LODESTONE_POS_TAG))
            this.lodestonePos = NbtUtils.readBlockPos(nbt.getCompound(LODESTONE_POS_TAG));
        if (nbt.contains(ITEM_TAG, 10)) {
            this.items.set(0, ItemStack.of(nbt.getCompound(ITEM_TAG)));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        if (lodestonePos != null) {
            nbt.put(LODESTONE_POS_TAG, NbtUtils.writeBlockPos(lodestonePos));
        } else {
            nbt.put(LODESTONE_POS_TAG, new CompoundTag());
        }

        if (!this.getFirstItem().isEmpty()) {
            nbt.put(ITEM_TAG, this.getFirstItem().save(new CompoundTag()));
        }
    }

    private void tick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide) return;

        boolean linked = false;

        if (lodestoneExists(level, pos)) {
            setLodestonePosition(null);
            if (hasLodestonePosition()) {
                Optional<Vec3> optional = findStandUpPosition(EntityType.PLAYER, level, lodestonePos);
                linked = optional.isPresent();
            }
        }


        EnderRelayBlock.setBlockState(level, state, LINKED, pos, null, linked);
    }

    public static void checkConnectionTick(Level pLevel, BlockPos pPos, BlockState pState, EnderRelayBlockEntity enderRelayBlockEntity) {
        enderRelayBlockEntity.tick(pLevel, pPos, pState);
    }

    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public boolean hasLodestonePosition() {
        return getLodestonePosition() != null;
    }

    public BlockPos getLodestonePosition() {
        return lodestonePos;
    }

    public void setLodestonePosition(@Nullable BlockPos pos) {
        this.lodestonePos = pos;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public boolean addFuel() {
        if (!isCharged()) {
            setItem(0, FUEL_ITEM.getDefaultInstance());
            return true;
        }

        return false;
    }

    public boolean isCharged() {
        return this.getFirstItem().is(FUEL_ITEM);
    }

    public boolean removeFuel() {
//        if (!isEmpty()) {
        removeItem(0, 1);
        return true;
//        }

//        return false;
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack itemStack = Objects.requireNonNullElse(this.items.get(slot), ItemStack.EMPTY);
        this.items.set(slot, ItemStack.EMPTY);
        if (!itemStack.isEmpty() && this.level != null) {
            EnderRelayBlock.setBlockState(getLevel(), getBlockState(), CHARGED, getBlockPos(), null, false);
            setChanged();
        }
        return itemStack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (stack.is(FUEL_ITEM) && this.level != null) {
            this.items.set(slot, stack);
            EnderRelayBlock.tryCharge(level, getBlockPos(), getBlockState());
            setChanged(level, getBlockPos(), getBlockState());
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        return stack.is(FUEL_ITEM) && this.getItem(index).isEmpty();
    }

    @Override
    public boolean canTakeItem(Container pTarget, int pIndex, ItemStack pStack) {
        return false;
    }
}