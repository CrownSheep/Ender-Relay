package net.crownsheep.ender_relay.block.entity;

import net.crownsheep.ender_relay.EnderRelay;
import net.crownsheep.ender_relay.block.ModBlocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, EnderRelay.MOD_ID);

    public static final RegistryObject<BlockEntityType<EnderRelayBlockEntity>> ENDER_RELAY_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("ender_relay_block_entity", () ->
                    BlockEntityType.Builder.of(EnderRelayBlockEntity::new,
                            ModBlocks.ENDER_RELAY.get()).build(null));


    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
