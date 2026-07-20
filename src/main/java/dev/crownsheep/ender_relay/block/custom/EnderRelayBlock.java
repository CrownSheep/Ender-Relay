package dev.crownsheep.ender_relay.block.custom;

import com.google.common.collect.ImmutableList;
import dev.crownsheep.ender_relay.EnderRelay;
import dev.crownsheep.ender_relay.advancement.ModCriteriaTriggers;
import dev.crownsheep.ender_relay.block.entity.EnderRelayBlockEntity;
import dev.crownsheep.ender_relay.block.entity.ModBlockEntities;
import dev.crownsheep.ender_relay.sound.ModSounds;
import dev.crownsheep.ender_relay.utils.LodestoneCompassUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Optional;

public class EnderRelayBlock extends BaseEntityBlock {

    public static final BooleanProperty CHARGED = BooleanProperty.create("charged");
    public static final BooleanProperty LINKED = BooleanProperty.create("linked");

    private static final ImmutableList<Vec3i> RESPAWN_HORIZONTAL_OFFSETS = ImmutableList.of(new Vec3i(0, 0, -1), new Vec3i(-1, 0, 0), new Vec3i(0, 0, 1), new Vec3i(1, 0, 0), new Vec3i(-1, 0, -1), new Vec3i(1, 0, -1), new Vec3i(-1, 0, 1), new Vec3i(1, 0, 1));
    private static final ImmutableList<Vec3i> RESPAWN_OFFSETS = (new ImmutableList.Builder<Vec3i>()).add(new Vec3i(0, 1, 0)).addAll(RESPAWN_HORIZONTAL_OFFSETS).addAll(RESPAWN_HORIZONTAL_OFFSETS.stream().map(Vec3i::below).iterator()).addAll(RESPAWN_HORIZONTAL_OFFSETS.stream().map(Vec3i::above).iterator()).add(new Vec3i(0, 1, 0)).build();

    public EnderRelayBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(CHARGED, false));
    }

    @Override
    public RenderShape getRenderShape(BlockState pState) {
        return RenderShape.MODEL;
    }

    public static boolean tryCharge(Level level, BlockPos pos, BlockState state) {
        if (!state.getValue(CHARGED)) {
            if (!level.isClientSide) {
                EnderRelayBlock.charge(level, pos, state);
            }
            return true;
        }
        return false;
    }

    private static void charge(Level level, BlockPos pos, BlockState state) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof EnderRelayBlockEntity enderRelayBlockEntity) {
            enderRelayBlockEntity.addFuel();
            EnderRelayBlock.setBlockState(level, state, CHARGED, pos, null, true);
            level.playSound(null,
                    (double) pos.getX() + 0.5D,
                    (double) pos.getY() + 0.5D,
                    (double) pos.getZ() + 0.5D,
                    ModSounds.ENDER_RELAY_CHARGE.get(), SoundSource.PLAYERS, 1.0F, 0.9F + level.random.nextFloat() * 0.2F);
        }
    }

    @Override
    public void animateTick(BlockState pState, Level pLevel, BlockPos pPos, RandomSource pRandom) {
        if (pState.getValue(CHARGED) && pState.getValue(LINKED) && pRandom.nextFloat() < 0.5F) {
            pLevel.addParticle(ParticleTypes.END_ROD, pPos.getX() + 0.5D + (2.0D * pRandom.nextDouble() - 1.0D),
                    pPos.getY() + (1.5D * pRandom.nextDouble() - 1.0D) + 0.7D, pPos.getZ() + 0.5D + (2.0D * pRandom.nextDouble() - 1.0D), 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos blockPos, BlockState blockState, Player player) {
        if (!level.isClientSide && !player.isCreative() && level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) {
            BlockEntity blockentity = level.getBlockEntity(blockPos);
            if (blockentity instanceof EnderRelayBlockEntity enderRelayBlockEntity) {
                ItemStack itemstack = new ItemStack(this);
                ItemStack heldItemstack = player.getMainHandItem();
                if (!EnchantmentHelper.hasSilkTouch(heldItemstack)) {
                    itemstack = getCompassItemStack(level, blockPos);
                } else {
                    if (enderRelayBlockEntity.hasLodestonePosition()) {
                        CompoundTag compoundTag = new CompoundTag();
                        compoundTag.put("LodestonePos", NbtUtils.writeBlockPos(enderRelayBlockEntity.getLodestonePosition()));
                        BlockItem.setBlockEntityData(itemstack, ModBlockEntities.ENDER_RELAY_BLOCK_ENTITY.get(), compoundTag);
                    }
                }

                popResource(level, blockPos, itemstack);
            }
        }

        super.playerWillDestroy(level, blockPos, blockState, player);
    }

    public InteractionResult use(BlockState blockState, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        ItemStack mainHandStack = player.getItemInHand(hand);

        if (hand == InteractionHand.MAIN_HAND && !isWarpFuel(mainHandStack) && isWarpFuel(player.getItemInHand(InteractionHand.OFF_HAND))) {
            return InteractionResult.PASS;
        } else if (isEndLodestoneCompass(mainHandStack) && !blockState.getValue(LINKED)) {
            ItemStack compassItem = getCompassItemStack(level, pos);
            BlockPos compassBlockPos = LodestoneCompassUtils.getLodestonePosition(mainHandStack);
            popResourceFromFace(level, pos, Direction.UP, compassItem);

            if (!player.getAbilities().instabuild) {
                mainHandStack.shrink(1);
            }

            setLodestonePos(level, pos, compassBlockPos);
            level.playSound(null, (double) pos.getX() + 0.5D, (double) pos.getY() + 0.5D, (double) pos.getZ() + 0.5D,
                    ModSounds.ENDER_RELAY_LOCK.get(), SoundSource.BLOCKS, 1.0F, 0.9F + level.random.nextFloat() * 0.2F);

            return InteractionResult.SUCCESS;
        } else if (isWarpFuel(mainHandStack) && canBeCharged(blockState)) {
            charge(level, pos, player);

            if (!player.getAbilities().instabuild) {
                mainHandStack.shrink(1);
            }

            return InteractionResult.sidedSuccess(level.isClientSide);
        } else if (canBeCharged(blockState)) {
            return InteractionResult.PASS;
        } else if (!isEnd(level)) {
            if (!level.isClientSide) {
                this.explode(level, pos);
            }

            return InteractionResult.sidedSuccess(level.isClientSide);
        } else {
            warp(player, level, pos, blockState);

            if (blockState.getValue(LINKED)) {
                return InteractionResult.sidedSuccess(level.isClientSide);
            } else {
                return InteractionResult.PASS;
            }
        }
    }

    private ItemStack getCompassItemStack(Level level, BlockPos pos) {
        ItemStack compassItemStack = new ItemStack(Items.COMPASS);
        BlockPos lodestonePos = getLodestonePos(level, pos);
        if (lodestonePos == null) {
            CompoundTag compassNbt = compassItemStack.getOrCreateTag();
            Level.RESOURCE_KEY_CODEC.encodeStart(NbtOps.INSTANCE, Level.END).resultOrPartial(EnderRelay.LOGGER::error).ifPresent((p_40731_) -> {
                compassNbt.put("LodestoneDimension", p_40731_);
            });
            compassNbt.putBoolean("LodestoneTracked", true);
        } else {
            ((CompassItem) Items.COMPASS).addLodestoneTags(Level.END, lodestonePos, compassItemStack.getOrCreateTag());
        }
        return compassItemStack;
    }

    private static boolean isWarpFuel(ItemStack itemStack) {
        return itemStack.is(EnderRelayBlockEntity.FUEL_ITEM);
    }

    public static boolean isEndLodestoneCompass(ItemStack itemStack) {
        CompoundTag compoundTag = itemStack.getTag();
        if (itemStack.is(Items.COMPASS) && compoundTag != null && CompassItem.getLodestonePosition(compoundTag) != null) {
            return CompassItem.isLodestoneCompass(itemStack) && LodestoneCompassUtils.isLodestoneInDimension(itemStack, Level.END);
        } else {
            return false;
        }
    }

    public static boolean canBeCharged(BlockState blockState) {
        return !blockState.getValue(CHARGED);
    }

    private void explode(Level level, final BlockPos blockPos) {
        level.removeBlock(blockPos, false);
        boolean flag = Direction.Plane.HORIZONTAL.stream().map(blockPos::relative).anyMatch((waterBlockPos) -> RespawnAnchorBlock.isWaterThatWouldFlow(waterBlockPos, level));
        final boolean flag1 = flag || level.getFluidState(blockPos.above()).is(FluidTags.WATER);
        ExplosionDamageCalculator explosiondamagecalculator = new ExplosionDamageCalculator() {
            public Optional<Float> getBlockExplosionResistance(Explosion explosion, BlockGetter blockGetter, BlockPos pos, BlockState state, FluidState fluidState) {
                return pos.equals(blockPos) && flag1 ? Optional.of(Blocks.WATER.getExplosionResistance()) : super.getBlockExplosionResistance(explosion, blockGetter, pos, state, fluidState);
            }
        };
        Vec3 vec3 = blockPos.getCenter();
        level.explode(null, level.damageSources().badRespawnPointExplosion(vec3), explosiondamagecalculator, vec3, 5.0F, true, Level.ExplosionInteraction.BLOCK);
    }

    public static boolean isEnd(Level level) {
        return level.dimension() == Level.END;
    }

    public static void setBlockState(Level level, BlockState blockState, Property<Boolean> property, BlockPos blockPos, Entity entity, boolean b) {
        BlockState blockstate = blockState.setValue(property, b);
        level.setBlock(blockPos, blockstate, 3);
        level.gameEvent(GameEvent.BLOCK_CHANGE, blockPos, GameEvent.Context.of(entity, blockstate));
        level.updateNeighborsAt(blockPos, blockState.getBlock());
    }

    public static void charge(Level level, BlockPos blockPos, @Nullable Player player) {
        BlockEntity blockEntity = level.getBlockEntity(blockPos);
        if (player != null)
            player.awardStat(Stats.ITEM_USED.get(Items.END_CRYSTAL));
        if (blockEntity instanceof EnderRelayBlockEntity enderRelayBlockEntity) {
            enderRelayBlockEntity.addFuel();
        }
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState pState) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState pState, Level pLevel, BlockPos pPos) {
        return pState.getValue(CHARGED) ? (pState.getValue(LINKED) ? 2 : 1) : 0;
    }

    public void warp(Player player, Level level, BlockPos blockPos, BlockState blockState) {
        if (!lodestoneExists(level, blockPos)) return;

        if (level instanceof ServerLevel serverLevel) {
            BlockPos lodestonePos = getLodestonePos(level, blockPos);
            Optional<Vec3> optional = findStandUpPosition(EntityType.PLAYER, serverLevel, lodestonePos);

            if (optional.isEmpty()) return;

            Vec3 destination = optional.get();

            ModCriteriaTriggers.USE_ENDER_RELAY.trigger((ServerPlayer) player);
            BlockEntity blockEntity = serverLevel.getBlockEntity(blockPos);
            if (!(blockEntity instanceof EnderRelayBlockEntity enderRelayBlockEntity)) return;

            if (player.isPassenger()) {
                player.stopRiding();
            }

            serverLevel.gameEvent(GameEvent.TELEPORT, new Vec3(lodestonePos.getX() + 0.5, lodestonePos.above().getY(), lodestonePos.getZ() + 0.5),
                    GameEvent.Context.of(player));

            player.teleportTo(destination.x, destination.y, destination.z);
            enderRelayBlockEntity.removeFuel();

            serverLevel.playSound(null, destination.x, destination.y, destination.z, ModSounds.ENDER_RELAY_TELEPORT.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }


    public static Optional<Vec3> findStandUpPosition(EntityType<?> pEntityType, CollisionGetter pLevel, BlockPos pPos) {
        Optional<Vec3> optional = findStandUpPosition(pEntityType, pLevel, pPos, true);
        return optional.isPresent() ? optional : findStandUpPosition(pEntityType, pLevel, pPos, false);
    }

    private static Optional<Vec3> findStandUpPosition(EntityType<?> pEntityType, CollisionGetter pLevel, BlockPos pPos, boolean pSimulate) {
        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();

        for (Vec3i vec3i : RESPAWN_OFFSETS) {
            blockPos.set(pPos).move(vec3i);
            Vec3 vec3 = DismountHelper.findSafeDismountLocation(pEntityType, pLevel, blockPos, pSimulate);
            if (vec3 != null) {
                return Optional.of(vec3);
            }
        }

        return Optional.empty();
    }

    public static BlockPos getLodestonePos(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof EnderRelayBlockEntity enderRelay) {
            return enderRelay.getLodestonePosition();
        }

        return null;
    }

    public static boolean hasLodestonePos(Level level, BlockPos pos) {
        BlockPos lodestonePos = getLodestonePos(level, pos);
        return lodestonePos != null;
    }

    public static boolean lodestoneExists(Level level, BlockPos blockPos) {
        if (level.dimension() != Level.END || !hasLodestonePos(level, blockPos))
            return false;

        BlockPos lodestonePos = getLodestonePos(level, blockPos);
        return level.isInWorldBounds(lodestonePos) && level.getBlockState(lodestonePos).is(Blocks.LODESTONE);
    }

    private void setLodestonePos(Level level, BlockPos blockPos, BlockPos lodestoneBlockPos) {
        BlockEntity blockEntity = level.getBlockEntity(blockPos);
        if (blockEntity instanceof EnderRelayBlockEntity enderRelay) {
            enderRelay.setLodestonePosition(lodestoneBlockPos);
        }
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> stateBuilder) {
        stateBuilder.add(CHARGED, LINKED);
    }

    public boolean isPathfindable(BlockState blockState, BlockGetter level, BlockPos blockPos, PathComputationType pathComputationType) {
        return false;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return new EnderRelayBlockEntity(blockPos, blockState);
    }

    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level pLevel, BlockState pState, BlockEntityType<T> pBlockEntityType) {
        return createTickerHelper(pBlockEntityType, ModBlockEntities.ENDER_RELAY_BLOCK_ENTITY.get(), EnderRelayBlockEntity::checkConnectionTick);
    }
}
