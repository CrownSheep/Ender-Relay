package net.crownsheep.ender_relay.recipe;

import net.crownsheep.ender_relay.block.ModBlocks;
import net.crownsheep.ender_relay.block.custom.EnderRelayBlock;
import net.crownsheep.ender_relay.block.entity.ModBlockEntities;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
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

public class EnderRelayRecipe extends CustomRecipe {
    private static final Ingredient OBSIDIAN_INGREDIENT = Ingredient.of(Items.OBSIDIAN);
    private static final Ingredient POPPED_CHORUS_FRUIT_INGREDIENT = Ingredient.of(Items.POPPED_CHORUS_FRUIT);

    public EnderRelayRecipe(CraftingBookCategory p_249010_) {
        super(p_249010_);
    }


    public boolean matches(CraftingContainer craftingContainer, Level level) {
        if (!this.canCraftInDimensions(craftingContainer.getWidth(), craftingContainer.getHeight())) {
            return false;
        } else {
            for (int i = 0; i < craftingContainer.getContainerSize(); ++i) {
                ItemStack itemstack = craftingContainer.getItem(i);
                switch (i) {
                    case 4:
                        if (!EnderRelayBlock.isEndLodestoneCompass(itemstack)) {
                            return false;
                        }
                        break;
                    case 0, 2, 6, 8:
                        if (!OBSIDIAN_INGREDIENT.test(itemstack)) {
                            return false;
                        }
                        break;
                    case 1, 3, 5, 7:
                        if (!POPPED_CHORUS_FRUIT_INGREDIENT.test(itemstack)) {
                            return false;
                        }
                }
            }
        }
        return true;
    }

    @Override
    public ItemStack assemble(CraftingContainer craftingContainer, RegistryAccess registryAccess) {
        ItemStack itemstack = new ItemStack(ModBlocks.ENDER_RELAY.get().asItem());
        CompoundTag compoundTag = itemstack.getOrCreateTag();
        compoundTag.put("LodestonePos", NbtUtils.writeBlockPos(CompassItem.getLodestonePosition(craftingContainer.getItem(4).getTag()).pos()));
        BlockItem.setBlockEntityData(itemstack, ModBlockEntities.ENDER_RELAY_BLOCK_ENTITY.get(), compoundTag);
        return itemstack;
    }

    public boolean canCraftInDimensions(int width, int height) {
        return width == 3 && height == 3;
    }

    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.ENDER_RELAY_SERIALIZER.get();
    }
}