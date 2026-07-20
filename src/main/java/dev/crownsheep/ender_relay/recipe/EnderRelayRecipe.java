package dev.crownsheep.ender_relay.recipe;

import dev.crownsheep.ender_relay.block.ModBlocks;
import dev.crownsheep.ender_relay.block.custom.EnderRelayBlock;
import dev.crownsheep.ender_relay.block.entity.ModBlockEntities;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import java.util.Set;

public class EnderRelayRecipe extends CustomRecipe {
    private static final Ingredient OBSIDIAN_INGREDIENT = Ingredient.of(Items.OBSIDIAN);
    private static final Ingredient POPPED_CHORUS_FRUIT_INGREDIENT = Ingredient.of(Items.POPPED_CHORUS_FRUIT);

    private static final Set<Integer> OBSIDIAN_SLOTS = Set.of(0, 2, 6, 8);
    private static final Set<Integer> CHORUS_SLOTS = Set.of(1, 3, 5, 7);
    private static final int COMPASS_SLOT = 4;

    public EnderRelayRecipe(ResourceLocation pId, CraftingBookCategory pCategory) {
        super(pId, pCategory);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        if (!canCraftInDimensions(container.getWidth(), container.getHeight()))
            return false;

        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);

            if (slot == COMPASS_SLOT) {
                if(stack.getItem() == Items.COMPASS) {
                    if (!EnderRelayBlock.isEndLodestoneCompass(stack))
                        return false;
                }
            } else if (OBSIDIAN_SLOTS.contains(slot)) {
                if (!OBSIDIAN_INGREDIENT.test(stack))
                    return false;
            } else if (CHORUS_SLOTS.contains(slot)) {
                if (!POPPED_CHORUS_FRUIT_INGREDIENT.test(stack))
                    return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack assemble(CraftingContainer craftingContainer, RegistryAccess registryAccess) {
        ItemStack itemstack = new ItemStack(ModBlocks.ENDER_RELAY.get().asItem());
        ItemStack compassItemStack = craftingContainer.getItem(COMPASS_SLOT);
        if(!compassItemStack.isEmpty()) {
            CompoundTag compoundTag = new CompoundTag();
            compoundTag.put("LodestonePos", NbtUtils.writeBlockPos(CompassItem.getLodestonePosition(compassItemStack.getTag()).pos()));
            BlockItem.setBlockEntityData(itemstack, ModBlockEntities.ENDER_RELAY_BLOCK_ENTITY.get(), compoundTag);
        }
        return itemstack;
    }

    public boolean canCraftInDimensions(int width, int height) {
        return width == 3 && height == 3;
    }

    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.ENDER_RELAY_SERIALIZER.get();
    }
}