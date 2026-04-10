package org.XYccWA.create_modern_technology.Radiation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;
import org.XYccWA.create_modern_technology.Create_modern_technology;
import org.XYccWA.create_modern_technology.Network.EnvironmentRadiationSyncPacket;
import org.XYccWA.create_modern_technology.World.EnvironmentRadiationData;
import org.XYccWA.create_modern_technology.World.RadiationSourceManager;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class RadiationUpdateThreadManager {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Radiation-Update-Thread");
        t.setDaemon(true);
        return t;
    });

    private static final Map<BlockPos, PendingUpdate> pendingUpdates = new ConcurrentHashMap<>();
    private static Level serverLevel = null;
    private static final AtomicBoolean isRunning = new AtomicBoolean(true);

    // 记录已发送同步的玩家
    private static final Map<ServerPlayer, Long> lastSyncTime = new ConcurrentHashMap<>();
    private static final int SYNC_INTERVAL_TICKS = 40; // 每2秒同步一次

    static {
        startUpdateThread();
    }

    private static void startUpdateThread() {
        EXECUTOR.submit(() -> {
            while (isRunning.get()) {
                try {
                    processPendingUpdates();
                    // 定期同步辐射数据到所有玩家
                    syncToAllPlayers();
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    public static void setServerLevel(Level level) {
        serverLevel = level;
    }

    public static void queueRadiationUpdate(BlockPos pos, boolean isAdding, int strength) {
        if (serverLevel == null) return;
        pendingUpdates.put(pos, new PendingUpdate(pos, isAdding, strength, System.currentTimeMillis()));
    }

    public static void queueFullRebuild() {
        if (serverLevel == null) return;
        pendingUpdates.put(new BlockPos(0, -1, 0), new PendingUpdate(null, true, 0, System.currentTimeMillis()));
    }

    private static void processPendingUpdates() {
        if (serverLevel == null) return;
        if (pendingUpdates.isEmpty()) return;

        for (Map.Entry<BlockPos, PendingUpdate> entry : pendingUpdates.entrySet()) {
            PendingUpdate update = entry.getValue();
            if (update.pos == null) {
                fullRebuildRadiationField();
            } else {
                updateRadiationField(update.pos, update.isAdding, update.strength);
            }
        }
        pendingUpdates.clear();

        // 更新完成后，强制同步给所有在线玩家
        forceSyncToAllPlayers();
    }

    private static void updateRadiationField(BlockPos center, boolean isAdding, int strength) {
        if (serverLevel == null) return;

        EnvironmentRadiationData envData = EnvironmentRadiationData.get(serverLevel);
        RadiationSourceManager sourceManager = RadiationSourceManager.get(serverLevel);

        if (!sourceManager.hasSources() && !isAdding) {
            envData.clearAll();
            return;
        }

        int radius = EnvironmentRadiationData.getDynamicRadius(strength);
        if (!isAdding) radius = 128;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos targetPos = center.offset(dx, dy, dz);
                    recalculateSinglePosition(targetPos, envData, sourceManager);
                }
            }
        }

        envData.setDirty();
    }

    private static void recalculateSinglePosition(BlockPos pos, EnvironmentRadiationData envData,
                                                  RadiationSourceManager sourceManager) {
        long totalIntensity = 0;

        for (Map.Entry<BlockPos, Integer> source : sourceManager.getAllSources().entrySet()) {
            BlockPos sourcePos = source.getKey();
            int sourceStrength = source.getValue();

            double distance = Math.sqrt(sourcePos.distSqr(pos));
            int maxRadius = EnvironmentRadiationData.getDynamicRadius(sourceStrength);

            if (distance <= maxRadius) {
                totalIntensity += (int) (sourceStrength / (distance * distance + 1));
            }
        }

        int finalIntensity = (int) Math.min(Integer.MAX_VALUE, totalIntensity);

        if (finalIntensity > 0) {
            int maxSourceStrength = sourceManager.getMaxStrength();
            int cap = (int)(maxSourceStrength * 1.3);
            finalIntensity = Math.min(cap, finalIntensity);
            envData.setRadiationAt(pos, finalIntensity);
        } else {
            envData.setRadiationAt(pos, 0);
        }
    }

    private static void fullRebuildRadiationField() {
        if (serverLevel == null) return;

        EnvironmentRadiationData envData = EnvironmentRadiationData.get(serverLevel);
        RadiationSourceManager sourceManager = RadiationSourceManager.get(serverLevel);

        if (!sourceManager.hasSources()) {
            envData.clearAll();
            return;
        }

        Map<BlockPos, Integer> allAffectedPositions = new ConcurrentHashMap<>();

        for (Map.Entry<BlockPos, Integer> source : sourceManager.getAllSources().entrySet()) {
            BlockPos sourcePos = source.getKey();
            int radius = EnvironmentRadiationData.getDynamicRadius(source.getValue());

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        BlockPos targetPos = sourcePos.offset(dx, dy, dz);
                        allAffectedPositions.put(targetPos, 0);
                    }
                }
            }
        }

        for (BlockPos pos : allAffectedPositions.keySet()) {
            recalculateSinglePosition(pos, envData, sourceManager);
        }

        envData.setDirty();
    }

    /**
     * 强制同步辐射数据给所有在线玩家
     */
    private static void forceSyncToAllPlayers() {
        if (serverLevel == null) return;
        if (!(serverLevel instanceof ServerLevel serverLevelObj)) return;

        EnvironmentRadiationData envData = EnvironmentRadiationData.get(serverLevel);

        serverLevelObj.players().forEach(player -> {
            syncRadiationToPlayer(player, envData);
        });
    }

    /**
     * 定期同步辐射数据给玩家（增量同步）
     */
    private static void syncToAllPlayers() {
        if (serverLevel == null) return;
        if (!(serverLevel instanceof ServerLevel serverLevelObj)) return;

        long currentTick = serverLevelObj.getGameTime();
        EnvironmentRadiationData envData = EnvironmentRadiationData.get(serverLevel);

        serverLevelObj.players().forEach(player -> {
            long lastSync = lastSyncTime.getOrDefault(player, 0L);
            if (currentTick - lastSync >= SYNC_INTERVAL_TICKS) {
                syncRadiationToPlayer(player, envData);
                lastSyncTime.put(player, currentTick);
            }
        });
    }

    /**
     * 同步单个玩家的辐射数据
     */
    private static void syncRadiationToPlayer(ServerPlayer player, EnvironmentRadiationData envData) {
        BlockPos playerPos = player.blockPosition();
        int syncRadius = 10;

        Map<BlockPos, Integer> syncData = new HashMap<>();

        // 收集玩家周围的辐射数据（步长4，提高精度）
        for (int dx = -syncRadius; dx <= syncRadius; dx += 4) {
            for (int dz = -syncRadius; dz <= syncRadius; dz += 4) {
                for (int dy = -8; dy <= 8; dy += 4) {
                    BlockPos pos = playerPos.offset(dx, dy, dz);
                    int radiation = envData.getRadiationAt(pos);
                    if (radiation > 0) {
                        syncData.put(pos, radiation);
                    }
                }
            }
        }

        // 确保同步当前玩家位置
        int currentRadiation = envData.getRadiationAt(playerPos);
        if (currentRadiation > 0) {
            syncData.put(playerPos, currentRadiation);
        }

        // 发送到客户端
        if (!syncData.isEmpty() || currentRadiation == 0) {
            Create_modern_technology.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new EnvironmentRadiationSyncPacket(syncData)
            );
        }
    }

    /**
     * 为指定玩家立即同步当前位置的辐射（用于方块破坏后）
     */
    public static void syncPlayerPosition(ServerPlayer player) {
        if (serverLevel == null) return;

        EnvironmentRadiationData envData = EnvironmentRadiationData.get(serverLevel);
        BlockPos playerPos = player.blockPosition();
        int radiation = envData.getRadiationAt(playerPos);

        Map<BlockPos, Integer> syncData = new HashMap<>();
        syncData.put(playerPos, radiation);

        // 同时同步周围的数据
        for (int dx = -8; dx <= 8; dx += 4) {
            for (int dz = -8; dz <= 8; dz += 4) {
                BlockPos pos = playerPos.offset(dx, 0, dz);
                int rad = envData.getRadiationAt(pos);
                if (rad > 0) {
                    syncData.put(pos, rad);
                }
            }
        }

        Create_modern_technology.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new EnvironmentRadiationSyncPacket(syncData)
        );
    }

    public static void shutdown() {
        isRunning.set(false);
        EXECUTOR.shutdown();
        try {
            if (!EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            EXECUTOR.shutdownNow();
        }
    }

    private static class PendingUpdate {
        final BlockPos pos;
        final boolean isAdding;
        final int strength;
        final long timestamp;

        PendingUpdate(BlockPos pos, boolean isAdding, int strength, long timestamp) {
            this.pos = pos;
            this.isAdding = isAdding;
            this.strength = strength;
            this.timestamp = timestamp;
        }
    }
}