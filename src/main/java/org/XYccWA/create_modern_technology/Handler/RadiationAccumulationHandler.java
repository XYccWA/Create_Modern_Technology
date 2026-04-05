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
import org.XYccWA.create_modern_technology.Network.EnvironmentRadiationSyncPacket;
import org.XYccWA.create_modern_technology.Util.RadiationHelper;
import org.XYccWA.create_modern_technology.World.EnvironmentRadiationData;

import java.util.HashMap;
import java.util.Map;

public class RadiationAccumulationHandler {

    private static final float ACCUMULATION_RATE = 0.05f;
    private static final int RADIATION_CHECK_INTERVAL = 20;      // 每秒检查一次（20 tick）
    private static final int DECAY_INTERVAL = 40;                // 每2秒衰减一次
    private static final float DECAY_RATE = 0.5f;                // 每次衰减0.5点
    private static final int SYNC_INTERVAL = 100;                // 每5秒同步一次环境辐射

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        Level level = player.level();

        if (level.isClientSide) return;

        // 同步环境辐射到客户端
        if (player.tickCount % SYNC_INTERVAL == 0 && player instanceof ServerPlayer serverPlayer) {
            syncEnvironmentRadiationToClient(serverPlayer);
        }

        // 辐射衰减（每2秒衰减0.5点）
        if (player.tickCount % DECAY_INTERVAL == 0) {
            decayRadiation(player);
        }

        // 辐射累积检查
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
            int newValue = Math.min(500, rad.getRadiation() + actualRadiation);
            rad.setRadiation(newValue);
            applyRadiationEffects(player, newValue);
        });
    }

    /**
     * 辐射自然衰减
     * 每秒下降0.5点（每2秒下降1点，使用DECAY_INTERVAL=40 tick）
     * 有辐射源时衰减速度减半（因为身体在持续受辐射）
     */
    private void decayRadiation(Player player) {
        player.getCapability(RadiationProvider.RADIATION_CAPABILITY).ifPresent(rad -> {
            int current = rad.getRadiation();
            if (current <= 0) return;

            // 检查玩家是否在辐射环境中
            EnvironmentRadiationData envData = EnvironmentRadiationData.get(player.level());
            int envIntensity = envData.getRadiationAt(player.blockPosition());

            // 计算衰减量
            float decayAmount = DECAY_RATE;

            // 在辐射环境中，衰减速度减半（身体持续受辐射，恢复变慢）
            if (envIntensity > 0) {
                decayAmount = DECAY_RATE * 0.5f;
            }

            // 辐射值越高，衰减速度稍微加快（身体代谢加速）
            if (current > 300) {
                decayAmount *= 1.2f;
            } else if (current > 150) {
                decayAmount *= 1.1f;
            }

            int newValue = (int) Math.max(0, current - decayAmount);
            if (newValue != current) {
                rad.setRadiation(newValue);
            }
        });
    }

    /**
     * 同步环境辐射数据到客户端
     */
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
        if (radiation >= 500) {
            player.hurt(player.damageSources().magic(), 4.0f);
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 3));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 2));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
        } else if (radiation >= 300) {
            if (player.getRandom().nextFloat() < 0.3f) {
                player.hurt(player.damageSources().magic(), 2.0f);
            }
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1));
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 200, 1));
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 1));
        } else if (radiation >= 150) {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0));
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 200, 1));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 0));
        } else if (radiation >= 50) {
            if (player.getRandom().nextFloat() < 0.1f) {
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 60, 0));
            }
        }
    }
}