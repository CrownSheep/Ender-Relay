package net.crownsheep.ender_relay.recipe;

import net.crownsheep.ender_relay.EnderRelay;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, EnderRelay.MOD_ID);

    public static final RegistryObject<RecipeSerializer<EnderRelayRecipe>> ENDER_RELAY_SERIALIZER =
            SERIALIZERS.register("crafting_ender_relay", () -> new SimpleCraftingRecipeSerializer<>(EnderRelayRecipe::new));

    public static void register(IEventBus eventBus) {
        SERIALIZERS.register(eventBus);
    }
}
