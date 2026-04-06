package org.XYccWA.create_modern_technology.Handler;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import org.XYccWA.create_modern_technology.Capability.RadiationProvider;
import org.XYccWA.create_modern_technology.Create_modern_technology;
import org.XYccWA.create_modern_technology.Damage.RadiationDamage;
import org.XYccWA.create_modern_technology.Network.EnvironmentRadiationSyncPacket;
import org.XYccWA.create_modern_technology.Util.RadiationHelper;
import org.XYccWA.create_modern_technology.World.EnvironmentRadiationData;

import java.util.HashMap;
import java.util.Map;

public class RadiationAccumulationHandler {

    private static final float ACCUMULATION_RATE = 0.05f;
    private static final int RADIATION_CHECK_INTERVAL = 20;
    private static final int DECAY_INTERVAL = 20;
    private static final float DECAY_RATE = 0.5f;
    private static final int SYNC_INTERVAL = 10;

    private static final int MAX_RADIATION = 1000;

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        Level level = player.level();

        if (level.isClientSide) return;

        if (player.tickCount % SYNC_INTERVAL == 0 && player instanceof ServerPlayer serverPlayer) {
            syncEnvironmentRadiationToClient(serverPlayer);
        }

        if (player.tickCount % DECAY_INTERVAL == 0) {
            decayRadiation(player);
        }

        if (player.tickCount % RADIATION_CHECK_INTERVAL != 0) return;

        BlockPos playerPos = player.blockPosition();

        EnvironmentRadiationData envData = EnvironmentRadiationData.get(level);
        int envIntensity = envData.getRadiationAt(playerPos);

        if (envIntensity <= 0) return;

        int leadCount = RadiationHelper.countLeadBlocksAroundPlayer(player);
        float attenuation = (float) Math.pow(0.5, leadCount);
        int actualRadiation = (int) (envIntensity * attenuation * ACCUMULATION_RATE);

        if (actualRadiation <= 0) return;

        player.getCapability(RadiationProvider.RADIATION_CAPABILITY).ifPresent(rad -> {
            int newValue = Math.min(MAX_RADIATION, rad.getRadiation() + actualRadiation);
            rad.setRadiation(newValue);
            applyRadiationEffects(player, newValue);
        });
    }

    private void decayRadiation(Player player) {
        player.getCapability(RadiationProvider.RADIATION_CAPABILITY).ifPresent(rad -> {
            int current = rad.getRadiation();
            if (current <= 0) return;

            EnvironmentRadiationData envData = EnvironmentRadiationData.get(player.level());
            int envIntensity = envData.getRadiationAt(player.blockPosition());

            float decayAmount = DECAY_RATE;

            if (envIntensity > 0) {
                decayAmount = DECAY_RATE * 0.5f;
            }

            if (current > 600) {
                decayAmount *= 1.2f;
            } else if (current > 300) {
                decayAmount *= 1.1f;
            }

            int newValue = (int) Math.max(0, current - decayAmount);
            if (newValue != current) {
                rad.setRadiation(newValue);
            }
        });
    }

    private void syncEnvironmentRadiationToClient(ServerPlayer player) {
        Level level = player.level();
        EnvironmentRadiationData envData = EnvironmentRadiationData.get(level);
        BlockPos playerPos = player.blockPosition();
        int syncRadius = 32;

        Map<BlockPos, Integer> syncData = new HashMap<>();

        for (int dx = -syncRadius; dx <= syncRadius; dx += 8) {
            for (int dz = -syncRadius; dz <= syncRadius; dz += 8) {
                BlockPos pos = playerPos.offset(dx, 0, dz);
                int radiation = envData.getRadiationAt(pos);
                if (radiation > 0) {
                    syncData.put(pos, radiation);
                }
            }
        }

        if (!syncData.isEmpty()) {
            Create_modern_technology.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new EnvironmentRadiationSyncPacket(syncData)
            );
        }
    }

    private void applyRadiationEffects(Player player, int radiation) {
        // 使用新的辐射伤害类型
        if (radiation >= 1000) {
            if (player.getRandom().nextFloat() < 0.3f) {
                player.hurt(RadiationDamage.cause(player.level()), (radiation-100)/20);
            }
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 3));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 2));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
        } else if (radiation >= 600) {
            // 重度
            if (player.getRandom().nextFloat() < 0.3f) {
                player.hurt(RadiationDamage.cause(player.level()), (radiation-100)/20);
            }
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1));
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 200, 1));
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 1));
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 200, 1));
        } else if (radiation >= 300) {
            // 中度
            if (player.getRandom().nextFloat() < 0.3f) {
                player.hurt(RadiationDamage.cause(player.level()), (radiation-100)/20);
            }
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0));
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 200, 1));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 0));
        } else if (radiation >= 100) {
            // 轻度
            if (player.getRandom().nextFloat() < 0.1f) {
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 60, 0));
            }
        }
    }
}