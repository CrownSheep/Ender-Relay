package dev.crownsheep.ender_relay.advancement;

import dev.crownsheep.ender_relay.EnderRelay;
import net.minecraft.advancements.critereon.PlayerTrigger;
import net.minecraft.resources.ResourceLocation;

import static net.minecraft.advancements.CriteriaTriggers.register;

public class ModCriteriaTriggers {
    public static final PlayerTrigger USE_ENDER_RELAY = new PlayerTrigger(ResourceLocation.fromNamespaceAndPath(EnderRelay.MOD_ID, "use_ender_relay"));

    public static void registerCriteriaTriggers() {
        register(USE_ENDER_RELAY);
    }
}