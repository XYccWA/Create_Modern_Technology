package org.XYccWA.create_modern_technology.Radiation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.XYccWA.create_modern_technology.World.EnvironmentRadiationData;
import org.XYccWA.create_modern_technology.World.RadiationSourceManager;

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

    // 待处理的辐射更新队列
    private static final Map<BlockPos, PendingUpdate> pendingUpdates = new ConcurrentHashMap<>();

    // 服务端世界引用
    private static Level serverLevel = null;

    // 线程运行状态
    private static final AtomicBoolean isRunning = new AtomicBoolean(true);

    static {
        startUpdateThread();
    }

    private static void startUpdateThread() {
        EXECUTOR.submit(() -> {
            while (isRunning.get()) {
                try {
                    processPendingUpdates();
                    Thread.sleep(50); // 每50ms处理一次
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

        // 批量处理更新
        for (Map.Entry<BlockPos, PendingUpdate> entry : pendingUpdates.entrySet()) {
            PendingUpdate update = entry.getValue();
            if (update.pos == null) {
                // 完整重建
                fullRebuildRadiationField();
            } else {
                // 增量更新
                updateRadiationField(update.pos, update.isAdding, update.strength);
            }
        }
        pendingUpdates.clear();
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

        // 同步执行辐射计算（在后台线程）
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

        // 收集所有需要更新的位置
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

        // 重新计算每个位置
        for (BlockPos pos : allAffectedPositions.keySet()) {
            recalculateSinglePosition(pos, envData, sourceManager);
        }

        envData.setDirty();
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