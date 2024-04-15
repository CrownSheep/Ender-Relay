package net.crownsheep.ender_relay.block.custom;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.MapCodec;
import net.crownsheep.ender_relay.EnderRelay;
import net.crownsheep.ender_relay.block.ModBlocks;
import net.crownsheep.ender_relay.block.entity.EnderRelayBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class EnderRelayBlock extends BaseEntityBlock {
    public static final MapCodec<EnderRelayBlock> CODEC = simpleCodec(EnderRelayBlock::new);
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }
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

    public EnderRelayBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(CHARGED, false));
    }

    @Override
    public RenderShape getRenderShape(BlockState pState) {
        return RenderShape.MODEL;
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos blockPos, BlockState blockState, @org.jetbrains.annotations.Nullable BlockEntity blockEntity, ItemStack itemStack) {
        super.playerDestroy(level, player, blockPos, blockState, blockEntity, itemStack);
        ItemStack dropStack;
        if(!level.isClientSide && blockEntity instanceof EnderRelayBlockEntity enderRelayBlockEntity) {
            if (EnchantmentHelper.getTagEnchantmentLevel(Enchantments.SILK_TOUCH, itemStack) == 0) {
                dropStack = new ItemStack(Items.COMPASS);
                CompoundTag compoundTag = new CompoundTag();
                compoundTag.put("LodestonePos", NbtUtils.writeBlockPos(enderRelayBlockEntity.getLodestonePosition()));
                Level.RESOURCE_KEY_CODEC.encodeStart(NbtOps.INSTANCE, Level.END).resultOrPartial(EnderRelay.LOGGER::error).ifPresent((tag) -> {
                    compoundTag.put("LodestoneDimension", tag);
                });
                compoundTag.putBoolean("LodestoneTracked", true);
                dropStack.setTag(compoundTag);
            } else {
                dropStack = new ItemStack(ModBlocks.ENDER_RELAY.get().asItem());
                CompoundTag compoundTag = new CompoundTag();
                compoundTag.put("LodestonePos", NbtUtils.writeBlockPos(enderRelayBlockEntity.getLodestonePosition()));
                dropStack.setTag(compoundTag);
            }
            Block.popResource(level, blockPos, dropStack);
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos blockPos, BlockState blockState, LivingEntity entity, ItemStack itemStack) {
        CompoundTag compoundtag = itemStack.getTag();
        if (compoundtag != null && compoundtag.contains("LodestonePos")) {
            setLodestoneLocation(level, blockPos, NbtUtils.readBlockPos(compoundtag.getCompound("LodestonePos")));
        }
    }

    public InteractionResult use(BlockState blockState, Level level, BlockPos blockPos, Player player, InteractionHand hand, BlockHitResult blockHitResult) {
        ItemStack itemstack = player.getItemInHand(hand);
        if (hand == InteractionHand.MAIN_HAND && !isWarpFuel(itemstack) && isWarpFuel(player.getItemInHand(InteractionHand.OFF_HAND))) {
            return InteractionResult.PASS;
        } else if (!level.isClientSide && !lodestoneExists(level, blockPos)) {
            if (canBeCharged(blockState) && !isWarpFuel(itemstack))
                return InteractionResult.PASS;

            if (getLodestonePos(level, blockPos) == null) {
                player.sendSystemMessage(Component.translatable("ender_relay.empty_location").withStyle(ChatFormatting.RED));
                return InteractionResult.CONSUME_PARTIAL;
            } else if (!lodestoneExists(level, blockPos)) {
                player.sendSystemMessage(Component.translatable("ender_relay.lodestone_destroyed").withStyle(ChatFormatting.RED));
                return InteractionResult.CONSUME_PARTIAL;
            }
            return InteractionResult.PASS;
        } else if (isWarpFuel(itemstack) && canBeCharged(blockState) && !level.isClientSide && lodestoneExists(level, blockPos)) {
            charge(level, blockPos);
            if (!player.getAbilities().instabuild) {
                itemstack.shrink(1);
            }

            return InteractionResult.SUCCESS;
        } else if (!blockState.getValue(CHARGED)) {
            return InteractionResult.PASS;
        } else if (!canSetSpawn(level)) {
            if (!level.isClientSide) {
                this.explode(level, blockPos);
            }

            return InteractionResult.sidedSuccess(level.isClientSide);
        } else {
            if (!level.isClientSide) {
                return useCrystal(player, level, blockPos, blockState, player);
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

    private static boolean isWaterThatWouldFlow(BlockPos blockPos, Level level) {
        FluidState fluidstate = level.getFluidState(blockPos);
        if (!fluidstate.is(FluidTags.WATER)) {
            return false;
        } else if (fluidstate.isSource()) {
            return true;
        } else {
            float f = (float) fluidstate.getAmount();
            if (f < 2.0F) {
                return false;
            } else {
                FluidState fluidstate1 = level.getFluidState(blockPos.below());
                return !fluidstate1.is(FluidTags.WATER);
            }
        }
    }

    private void explode(Level level, final BlockPos blockPos) {
        level.removeBlock(blockPos, false);
        boolean flag = Direction.Plane.HORIZONTAL.stream().map(blockPos::relative).anyMatch((waterBlockPos) -> isWaterThatWouldFlow(waterBlockPos, level));
        final boolean flag1 = flag || level.getFluidState(blockPos.above()).is(FluidTags.WATER);
        ExplosionDamageCalculator explosiondamagecalculator = new ExplosionDamageCalculator() {
            public Optional<Float> getBlockExplosionResistance(Explosion p_55904_, BlockGetter p_55905_, BlockPos p_55906_, BlockState p_55907_, FluidState p_55908_) {
                return p_55906_.equals(blockPos) && flag1 ? Optional.of(Blocks.WATER.getExplosionResistance()) : super.getBlockExplosionResistance(p_55904_, p_55905_, p_55906_, p_55907_, p_55908_);
            }
        };
        Vec3 vec3 = blockPos.getCenter();
        level.explode(null, level.damageSources().badRespawnPointExplosion(vec3), explosiondamagecalculator, vec3, 5.0F, true, Level.ExplosionInteraction.BLOCK);
    }

    public static boolean canSetSpawn(Level p_55851_) {
        return p_55851_.dimension() == Level.END;
    }

    public static void setChargedBlockstate(Level level, BlockState blockState, BlockPos blockPos, Entity entity, boolean charged) {
        BlockState blockstate = blockState.setValue(CHARGED, charged);
        level.setBlock(blockPos, blockstate, 3);
        level.gameEvent(GameEvent.BLOCK_CHANGE, blockPos, GameEvent.Context.of(entity, blockstate));
    }

    public static void charge(Level level, BlockPos blockPos) {
        BlockEntity blockEntity = level.getBlockEntity(blockPos);
        if (blockEntity instanceof EnderRelayBlockEntity enderRelayBlockEntity && enderRelayBlockEntity.getLodestonePosition() != null && lodestoneExists(level, blockPos)) {
            enderRelayBlockEntity.addCrystal();
        }
    }

    public boolean hasAnalogOutputSignal(BlockState p_55860_) {
        return true;
    }

    public int getAnalogOutputSignal(BlockState blockState, Level level, BlockPos blockPos) {
        return blockState.getValue(EnderRelayBlock.CHARGED) ? 15 : 0;
    }

    public InteractionResult useCrystal(@Nullable Entity entity, Level level, BlockPos blockPos, BlockState blockState, Player player) {
        BlockPos lodestonePos = getLodestonePos(level, blockPos);
        if (lodestonePos != null && lodestoneExists(level, blockPos)) {
            if (level instanceof ServerLevel serverLevel) {
                Optional<Vec3> optional = findStandUpPosition(EntityType.PLAYER, serverLevel, getLodestonePos(level, blockPos));
                if (optional.isPresent()) {
                    if ((int) Math.floor(optional.get().x) == player.getBlockX() && (int) Math.floor(optional.get().y) == player.getBlockY() && (int) Math.floor(optional.get().z) == player.getBlockZ()) {
                        player.sendSystemMessage(Component.translatable("ender_relay.same_location").withStyle(ChatFormatting.RED));
                        return InteractionResult.CONSUME_PARTIAL;
                    } else {
                        BlockEntity blockEntity = serverLevel.getBlockEntity(blockPos);
                        if (blockEntity instanceof EnderRelayBlockEntity) {
                            ((EnderRelayBlockEntity) blockEntity).removeCrystal();
                        }
                        setChargedBlockstate(level, blockState, blockPos, entity, false);
                        player.teleportTo(optional.get().x, optional.get().y, optional.get().z);
                        serverLevel.gameEvent(GameEvent.TELEPORT, new Vec3(lodestonePos.getX() + 0.5, lodestonePos.above().getY(), lodestonePos.getZ() + 0.5), GameEvent.Context.of(player));
                        serverLevel.playSound(null, player.getX() + 0.5D, player.getY(), player.getZ() + 0.5D, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
                        return InteractionResult.SUCCESS;
                    }
                } else {
                    player.sendSystemMessage(!isInWorldBounds(level, lodestonePos) ? Component.translatable("ender_relay.out_of_bounds").withStyle(ChatFormatting.RED) : Component.translatable("ender_relay.obstructed").withStyle(ChatFormatting.RED));
                    return InteractionResult.CONSUME_PARTIAL;
                }
            }
        }
        return InteractionResult.CONSUME_PARTIAL;
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
            BlockPos lodestonePos = getLodestonePos(level, blockPos);
            if (lodestonePos != null) {
                return level.dimension() == Level.END && ((ServerLevel) level).getPoiManager().existsAtPosition(PoiTypes.LODESTONE, lodestonePos);
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
