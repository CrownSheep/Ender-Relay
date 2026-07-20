package dev.crownsheep.ender_relay.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

public class LodestoneCompassUtils {
    public static boolean isLodestoneInDimension(ItemStack itemStack, ResourceKey<Level> dimension) {
        if(CompassItem.isLodestoneCompass(itemStack)) {
            GlobalPos pos = CompassItem.getLodestonePosition(itemStack.getTag());

            if(pos != null)
                return pos.dimension() == dimension;
        }

        return false;
    }

    @Nullable
    public static BlockPos getLodestonePosition(ItemStack itemStack) {
        if(CompassItem.isLodestoneCompass(itemStack)) {
            GlobalPos pos = CompassItem.getLodestonePosition(itemStack.getTag());

            if(pos != null)
                return pos.pos();
        }

        return null;
    }
}
