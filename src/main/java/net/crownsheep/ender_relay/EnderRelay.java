package net.crownsheep.ender_relay;

import com.mojang.logging.LogUtils;
import net.crownsheep.ender_relay.advancement.ModCriteriaTriggers;
import net.crownsheep.ender_relay.block.ModBlocks;
import net.crownsheep.ender_relay.block.custom.EnderRelayBlock;
import net.crownsheep.ender_relay.block.entity.ModBlockEntities;
import net.crownsheep.ender_relay.recipe.ModRecipes;
import net.crownsheep.ender_relay.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.OptionalDispenseItemBehavior;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(EnderRelay.MOD_ID)
public class EnderRelay {

    // Define mod id in a common place for everything to reference
    public static final String MOD_ID = "ender_relay";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    public EnderRelay() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModSounds.register(modEventBus);
        ModRecipes.register(modEventBus);
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::addToCreativeTab);
        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(ModCriteriaTriggers::registerCriteriaTriggers);
        DispenserBlock.registerBehavior(Items.END_CRYSTAL, new OptionalDispenseItemBehavior() {
            public ItemStack execute(BlockSource blockSource, ItemStack itemStack) {
                Direction direction = blockSource.state().getValue(DispenserBlock.FACING);
                BlockPos blockpos = blockSource.pos().relative(direction);
                Level level = blockSource.level();
                BlockState blockstate = level.getBlockState(blockpos);
                this.setSuccess(true);
                if (blockstate.is(ModBlocks.ENDER_RELAY.get())) {
                    if (!blockstate.getValue(EnderRelayBlock.CHARGED)) {
                        EnderRelayBlock.charge(level, blockpos, null);
                        itemStack.shrink(1);
                    } else {
                        this.setSuccess(false);
                    }

                    return itemStack;
                } else {
                    return super.execute(blockSource, itemStack);
                }
            }
        });
    }

    private void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(new ItemStack(ModBlocks.ENDER_RELAY.get()));
        }
    }
}
