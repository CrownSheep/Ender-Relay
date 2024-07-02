package net.crownsheep.ender_relay.advancement;

import net.minecraft.advancements.critereon.PlayerTrigger;

import static net.minecraft.advancements.CriteriaTriggers.register;

public class ModCriteriaTriggers {
    public static final PlayerTrigger USE_ENDER_RELAY = new PlayerTrigger();

    public static void registerCriteriaTriggers() {
        register("use_ender_relay", USE_ENDER_RELAY);
    }
}