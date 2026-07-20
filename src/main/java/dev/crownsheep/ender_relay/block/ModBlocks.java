package dev.crownsheep.ender_relay.block;

import dev.crownsheep.ender_relay.EnderRelay;
import dev.crownsheep.ender_relay.block.custom.EnderRelayBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, EnderRelay.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, EnderRelay.MOD_ID);

    public static final RegistryObject<Block> ENDER_RELAY = registerBlock("ender_relay",
            () -> new EnderRelayBlock(BlockBehaviour.Properties.copy(Blocks.RESPAWN_ANCHOR)
                    .lightLevel((blockState) -> blockState.getValue(EnderRelayBlock.CHARGED) ?
                            (blockState.getValue(EnderRelayBlock.LINKED) ? 15 : 7) : 0)), true);

    private static <T extends Block> RegistryObject<T> registerBlock(String name, Supplier<T> block, boolean registerItem) {
        RegistryObject<T> toReturn = BLOCKS.register(name, block);
        if(registerItem)
            registerBlockItem(name, toReturn);

        return toReturn;
    }

    private static <T extends Block> RegistryObject<Item> registerBlockItem(String name, RegistryObject<T> block) {
        return ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
    }
}