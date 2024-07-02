package net.crownsheep.ender_relay.block.custom;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.MapCodec;
import net.crownsheep.ender_relay.advancement.ModCriteriaTriggers;
import net.crownsheep.ender_relay.block.ModBlocks;
import net.crownsheep.ender_relay.block.entity.EnderRelayBlockEntity;
import net.crownsheep.ender_relay.block.entity.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Optional;

public class EnderRelayBlock extends BaseEntityBlock {
    public static final MapCodec<EnderRelayBlock> CODEC = simpleCodec(EnderRelayBlock::new);

    protected MapCodec<EnderRelayBlock> codec() { return CODEC; }

    public static final BooleanProperty CHARGED = BooleanProperty.create("charged");

    private static final ImmutableList<Vec3i> RESPAWN_HORIZONTAL_OFFSETS = ImmutableList.of(
            new Vec3i(0, 1, 0),
            new Vec3i(0, 0, -1),
            new Vec3i(-1, 0, 0),
            new Vec3i(0, 0, 1),
            new Vec3i(1, 0, 0),
            new Vec3i(-1, 0, -1),
            new Vec3i(1, 0, -1),
            new Vec3i(-1, 0, 1));

    public EnderRelayBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(CHARGED, false));
    }

    @Override
    public RenderShape getRenderShape(BlockState pState) {
        return RenderShape.MODEL;
    }


    @Override
    public BlockState playerWillDestroy(Level level, BlockPos blockPos, BlockState blockState, Player player) {
        ItemStack itemstack = player.getItemInHand(InteractionHand.MAIN_HAND);
        BlockEntity blockEntity = level.getBlockEntity(blockPos);
        if (!(blockEntity instanceof EnderRelayBlockEntity enderRelayEntity))
            return super.playerWillDestroy(level, blockPos, blockState, player);
        if (enderRelayEntity.hasNoLodestonePosition()) return super.playerWillDestroy(level, blockPos, blockState, player);
        if (player.getAbilities().instabuild) return super.playerWillDestroy(level, blockPos, blockState, player);

        ItemStack dropStack;
        if (EnchantmentHelper.getTagEnchantmentLevel(Enchantments.SILK_TOUCH, itemstack) == 0) {
            dropStack = new ItemStack(Items.COMPASS);
            ((CompassItem) Items.COMPASS).addLodestoneTags(level.dimension(), enderRelayEntity.getLodestonePosition(), dropStack.getOrCreateTag());
        } else {
            dropStack = new ItemStack(ModBlocks.ENDER_RELAY.get().asItem());
            CompoundTag compoundTag = new CompoundTag();
            compoundTag.put("LodestonePos", NbtUtils.writeBlockPos(enderRelayEntity.getLodestonePosition()));
            BlockItem.setBlockEntityData(dropStack, ModBlockEntities.ENDER_RELAY_BLOCK_ENTITY.get(), compoundTag);
        }
        popResource(level, blockPos, dropStack);
        return super.playerWillDestroy(level, blockPos, blockState, player);
    }

    public InteractionResult use(BlockState blockState, Level level, BlockPos blockPos, Player player, InteractionHand hand, BlockHitResult blockHitResult) {
        ItemStack itemstack = player.getItemInHand(hand);
        if (hand == InteractionHand.MAIN_HAND && !isWarpFuel(itemstack) && isWarpFuel(player.getItemInHand(InteractionHand.OFF_HAND))) {
            return InteractionResult.PASS;
        } else if (!level.isClientSide && isEnd(level) && !lodestoneExists(level, blockPos)) {
            if (getLodestonePos(level, blockPos) == null) {
                if (isWarpFuel(itemstack)) {
                    player.displayClientMessage(Component.translatable("ender_relay.empty_location").withStyle(ChatFormatting.RED), false);
                    return InteractionResult.FAIL;
                }
            } else if (!lodestoneExists(level, blockPos)) {
                if (isWarpFuel(itemstack) || !canBeCharged(blockState)) {
                    player.displayClientMessage(Component.translatable("ender_relay.lodestone_destroyed").withStyle(ChatFormatting.RED), false);
                    return InteractionResult.FAIL;
                }
            }
            return InteractionResult.FAIL;
        } else if (!level.isClientSide && isWarpFuel(itemstack) && canBeCharged(blockState)) {
            charge(level, blockPos, player);
            if (!player.getAbilities().instabuild) {
                itemstack.shrink(1);
            }

            return InteractionResult.SUCCESS;
        } else if (canBeCharged(blockState)) {
            return InteractionResult.PASS;
        } else if (!isEnd(level)) {
            if (!level.isClientSide)
                this.explode(level, blockPos);

            return InteractionResult.sidedSuccess(level.isClientSide);
        } else {
            if (!level.isClientSide) {
                return warp(player, level, blockPos);
            }

            return InteractionResult.CONSUME;
        }
    }

    private static boolean isWarpFuel(ItemStack itemStack) {
        return itemStack.is(Items.END_CRYSTAL);
    }

    public static boolean isEndLodestoneCompass(ItemStack itemStack) {
        CompoundTag compoundTag = itemStack.getTag();
        if (itemStack.is(Items.COMPASS) && compoundTag != null && CompassItem.getLodestonePosition(compoundTag) != null) {
            return CompassItem.isLodestoneCompass(itemStack) && CompassItem.getLodestonePosition(compoundTag).dimension() == Level.END;
        } else {
            return false;
        }
    }

    private static boolean canBeCharged(BlockState blockState) {
        return !blockState.getValue(CHARGED);
    }

    private void explode(Level level, final BlockPos blockPos) {
        level.removeBlock(blockPos, false);
        boolean flag = Direction.Plane.HORIZONTAL.stream().map(blockPos::relative).anyMatch((waterBlockPos) -> RespawnAnchorBlock.isWaterThatWouldFlow(waterBlockPos, level));
        final boolean flag1 = flag || level.getFluidState(blockPos.above()).is(FluidTags.WATER);
        ExplosionDamageCalculator explosiondamagecalculator = new ExplosionDamageCalculator() {
            public Optional<Float> getBlockExplosionResistance(Explosion p_55904_, BlockGetter p_55905_, BlockPos p_55906_, BlockState p_55907_, FluidState p_55908_) {
                return p_55906_.equals(blockPos) && flag1 ? Optional.of(Blocks.WATER.getExplosionResistance()) : super.getBlockExplosionResistance(p_55904_, p_55905_, p_55906_, p_55907_, p_55908_);
            }
        };
        Vec3 vec3 = blockPos.getCenter();
        level.explode(null, level.damageSources().badRespawnPointExplosion(vec3), explosiondamagecalculator, vec3, 5.0F, true, Level.ExplosionInteraction.BLOCK);
    }

    public static boolean isEnd(Level level) {
        return level.dimension() == Level.END;
    }

    public static void setChargedBlockstate(Level level, BlockState blockState, BlockPos blockPos, Entity entity, boolean charged) {
        BlockState blockstate = blockState.setValue(CHARGED, charged);
        level.setBlock(blockPos, blockstate, 3);
        level.gameEvent(GameEvent.BLOCK_CHANGE, blockPos, GameEvent.Context.of(entity, blockstate));
        level.updateNeighborsAt(blockPos, blockState.getBlock());
    }

    public static void charge(Level level, BlockPos blockPos, @Nullable Player player) {
        BlockEntity blockEntity = level.getBlockEntity(blockPos);
        if (player != null)
            player.awardStat(Stats.ITEM_USED.get(Items.END_CRYSTAL));
        if (blockEntity instanceof EnderRelayBlockEntity enderRelayBlockEntity) {
            enderRelayBlockEntity.addCrystal();
        }
    }

    public boolean hasAnalogOutputSignal(BlockState p_54503_) {
        return true;
    }

    public int getAnalogOutputSignal(BlockState blockState, Level p_54521_, BlockPos p_54522_) {
        return blockState.getValue(CHARGED) ? 15 : 0;
    }

    public InteractionResult warp(Player player, Level level, BlockPos blockPos) {
        BlockPos lodestonePos = getLodestonePos(level, blockPos);
        if (lodestonePos != null && lodestoneExists(level, blockPos)) {
            if (level instanceof ServerLevel serverLevel) {
                Optional<Vec3> optional = findStandUpPosition(EntityType.PLAYER, serverLevel, getLodestonePos(level, blockPos));
                if (optional.isPresent()) {
                    if ((int) Math.floor(optional.get().x) == player.getBlockX() && (int) Math.floor(optional.get().y) == player.getBlockY() && (int) Math.floor(optional.get().z) == player.getBlockZ()) {
                        player.displayClientMessage(Component.translatable("ender_relay.same_location").withStyle(ChatFormatting.RED), false);
                        return InteractionResult.FAIL;
                    } else {
                        ModCriteriaTriggers.USE_ENDER_RELAY.trigger((ServerPlayer) player);
                        BlockEntity blockEntity = serverLevel.getBlockEntity(blockPos);
                        if (blockEntity instanceof EnderRelayBlockEntity) {
                            ((EnderRelayBlockEntity) blockEntity).removeCrystal();
                        }
                        player.teleportTo(optional.get().x, optional.get().y, optional.get().z);
                        serverLevel.gameEvent(GameEvent.TELEPORT, new Vec3(lodestonePos.getX() + 0.5, lodestonePos.above().getY(), lodestonePos.getZ() + 0.5), GameEvent.Context.of(player));
                        serverLevel.playSound(null, player.getX() + 0.5D, player.getY(), player.getZ() + 0.5D, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
                        return InteractionResult.SUCCESS;
                    }
                } else {
                    player.displayClientMessage(!isInWorldBounds(level, lodestonePos) ? Component.translatable("ender_relay.out_of_bounds").withStyle(ChatFormatting.RED) : Component.translatable("ender_relay.obstructed").withStyle(ChatFormatting.RED), false);
                    return InteractionResult.FAIL;
                }
            }
        }
        return InteractionResult.PASS;
    }

    private static boolean isInWorldBounds(Level level, BlockPos blockPos) {
        return blockPos.getX() >= -level.getWorldBorder().getMaxX() && blockPos.getZ() >= -level.getWorldBorder().getMaxZ() && blockPos.getX() < level.getWorldBorder().getMaxX() && blockPos.getZ() < level.getWorldBorder().getMaxZ();
    }

    public static Optional<Vec3> findStandUpPosition(EntityType<?> entityType, CollisionGetter collisionGetter, BlockPos blockPos) {
        Optional<Vec3> optional = findStandUpPosition(entityType, collisionGetter, blockPos, true);
        return optional.isPresent() ? optional : findStandUpPosition(entityType, collisionGetter, blockPos, false);
    }

    private static Optional<Vec3> findStandUpPosition(EntityType<?> p_55844_, CollisionGetter p_55845_, BlockPos p_55846_, boolean p_55847_) {
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();

        for (Vec3i vec3i : RESPAWN_HORIZONTAL_OFFSETS) {
            blockpos$mutableblockpos.set(p_55846_).move(vec3i);
            Vec3 vec3 = DismountHelper.findSafeDismountLocation(p_55844_, p_55845_, blockpos$mutableblockpos, p_55847_);
            if (vec3 != null) {
                return Optional.of(vec3);
            }
        }

        return Optional.empty();
    }

    public static BlockPos getLodestonePos(Level level, BlockPos blockPos) {
        BlockEntity blockEntity = level.getBlockEntity(blockPos);
        if (blockEntity instanceof EnderRelayBlockEntity) {
            return ((EnderRelayBlockEntity) blockEntity).getLodestonePosition();
        }
        return null;
    }

    public static boolean lodestoneExists(Level level, BlockPos blockPos) {
        if (!level.isClientSide) {
            ServerLevel end = level.getServer().getLevel(Level.END);
            BlockPos lodestonePos = getLodestonePos(level, blockPos);
            if (lodestonePos != null) {
                return end.getPoiManager().existsAtPosition(PoiTypes.LODESTONE, lodestonePos);
            }
        }
        return false;
    }

    private void setLodestoneLocation(Level level, BlockPos blockPos, BlockPos lodestoneBlockPos) {
        BlockEntity blockEntity = level.getBlockEntity(blockPos);
        if (blockEntity instanceof EnderRelayBlockEntity) {
            ((EnderRelayBlockEntity) blockEntity).setLodestonePosition(lodestoneBlockPos);
        }
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> stateBuilder) {
        stateBuilder.add(CHARGED);
    }

    public boolean isPathfindable(BlockState blockState, BlockGetter level, BlockPos blockPos, PathComputationType pathComputationType) {
        return false;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return new EnderRelayBlockEntity(blockPos, blockState);
    }
}
