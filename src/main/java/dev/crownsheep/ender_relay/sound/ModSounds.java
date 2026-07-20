package dev.crownsheep.ender_relay.sound;

import dev.crownsheep.ender_relay.EnderRelay;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, EnderRelay.MOD_ID);

    public static final RegistryObject<SoundEvent> ENDER_RELAY_CHARGE = registerSoundEvent("ender_relay_charge");
    public static final RegistryObject<SoundEvent> ENDER_RELAY_LOCK = registerSoundEvent("ender_relay_lock");
    public static final RegistryObject<SoundEvent> ENDER_RELAY_TELEPORT = registerSoundEvent("ender_relay_teleport");

    private static RegistryObject<SoundEvent> registerSoundEvent(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(EnderRelay.MOD_ID, name)));
    }

    public static void register(IEventBus eventBus) {
        SOUND_EVENTS.register(eventBus);
    }
}
