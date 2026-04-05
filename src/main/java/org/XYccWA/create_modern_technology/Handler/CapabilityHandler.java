package org.XYccWA.create_modern_technology.Handler;

import org.XYccWA.create_modern_technology.Capability.IRadiation;
import org.XYccWA.create_modern_technology.Capability.RadiationProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class CapabilityHandler {
    private static final ResourceLocation RADIATION_KEY = new ResourceLocation("radiation", "radiation_data");

    @SubscribeEvent
    public void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(IRadiation.class);
    }

    @SubscribeEvent
    public void attachPlayerCapability(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof ServerPlayer player) {
            event.addCapability(RADIATION_KEY, new RadiationProvider(player));
        } else if (event.getObject() instanceof Player) {
            // 客户端玩家使用无参构造
            event.addCapability(RADIATION_KEY, new RadiationProvider());
        }
    }

    // 玩家重生时重新关联
    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getOriginal().level().isClientSide) return;

        ServerPlayer original = (ServerPlayer) event.getOriginal();
        ServerPlayer clone = (ServerPlayer) event.getEntity();

        original.getCapability(RadiationProvider.RADIATION_CAPABILITY).ifPresent(oldRad -> {
            clone.getCapability(RadiationProvider.RADIATION_CAPABILITY).ifPresent(newRad -> {
                newRad.setRadiation(oldRad.getRadiation());
            });
        });
    }
}