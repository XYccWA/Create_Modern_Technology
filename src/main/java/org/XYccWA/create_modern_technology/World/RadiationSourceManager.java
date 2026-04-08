package org.XYccWA.create_modern_technology.World;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.XYccWA.create_modern_technology.Radiation.RadiationThreadPool;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class RadiationSourceManager extends SavedData {

    // 辐射源缓存（使用 ConcurrentHashMap 支持并发）
    private final Map<BlockPos, Integer> radiationSources = new ConcurrentHashMap<>();

    // 环境辐射缓存
    private final Map<BlockPos, Integer> radiationCache = new ConcurrentHashMap<>();

    // 更新锁（防止并发更新）
    private final AtomicBoolean isUpdating = new AtomicBoolean(false);

    // 待处理的更新队列
    private final Queue<Runnable> pendingUpdates = new ConcurrentLinkedQueue<>();

    public void addSource(BlockPos pos, int strength) {
        radiationSources.put(pos, Math.min(100000, Math.max(0, strength)));
        setDirty();
    }

    public void removeSource(BlockPos pos) {
        radiationSources.remove(pos);
        setDirty();
    }

    public Map<BlockPos, Integer> getAllSources() {
        return Collections.unmodifiableMap(radiationSources);
    }

    public boolean hasSources() {
        return !radiationSources.isEmpty();
    }

    public int getMaxStrength() {
        return radiationSources.values().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
    }

    /**
     * 异步更新受影响区域（优化版）
     */
    public void updateAffectedAreaAsync(Level level, BlockPos changedPos, boolean isAdding) {
        if (level.isClientSide) return;

        // 提交到后台线程
        RadiationThreadPool.submitUpdate(level, changedPos, isAdding, 0);

        // 批量收集更新（用于同时破坏多个方块）
        // 实际实现中，可以通过事件系统批量处理
    }

    /**
     * 批量更新多个位置
     */
    public void batchUpdateAsync(Map<BlockPos, Boolean> changes) {
        if (changes.isEmpty()) return;

        Map<RadiationThreadPool.UpdateData, Void> updates = new HashMap<>();
        for (Map.Entry<BlockPos, Boolean> entry : changes.entrySet()) {
            BlockPos pos = entry.getKey();
            boolean isAdding = entry.getValue();
            updates.put(new RadiationThreadPool.UpdateData(pos, 0, isAdding), null);
        }

        // 提交批量更新
        // RadiationThreadPool.submitBatchUpdate(updates);
    }

    /**
     * 增量更新（在主线程执行，但使用增量计算）
     */
    public void updateAffectedAreaIncremental(Level level, BlockPos changedPos, boolean isAdding) {
        if (level.isClientSide) return;

        // 使用 CAS 避免重复更新
        if (!isUpdating.compareAndSet(false, true)) {
            pendingUpdates.offer(() -> updateAffectedAreaIncremental(level, changedPos, isAdding));
            return;
        }

        try {
            EnvironmentRadiationData envData = EnvironmentRadiationData.get(level);

            if (!hasSources()) {
                envData.clearAll();
                return;
            }

            // 只更新变化位置周围的区域（使用较小的范围）
            int updateRadius = 32;  // 固定范围，避免动态计算开销

            // 使用并行流加速（但注意 BlockPos 操作不是线程安全的，需要创建新对象）
            Set<BlockPos> positionsToUpdate = new HashSet<>();
            for (int dx = -updateRadius; dx <= updateRadius; dx += 2) {  // 步长2，减少计算量
                for (int dy = -updateRadius; dy <= updateRadius; dy += 2) {
                    for (int dz = -updateRadius; dz <= updateRadius; dz += 2) {
                        positionsToUpdate.add(changedPos.offset(dx, dy, dz));
                    }
                }
            }

            // 批量重新计算
            for (BlockPos targetPos : positionsToUpdate) {
                recalculateSinglePosition(level, targetPos, envData);
            }

            envData.setDirty();
        } finally {
            isUpdating.set(false);

            // 处理待处理的更新
            Runnable next = pendingUpdates.poll();
            if (next != null) {
                next.run();
            }
        }
    }

    /**
     * 重新计算单个位置的辐射强度（优化版）
     */
    private void recalculateSinglePosition(Level level, BlockPos pos, EnvironmentRadiationData envData) {
        if (!hasSources()) {
            envData.setRadiationAt(pos, 0);
            return;
        }

        long totalIntensity = 0;

        // 使用缓存的辐射源列表
        for (Map.Entry<BlockPos, Integer> source : radiationSources.entrySet()) {
            BlockPos sourcePos = source.getKey();
            int sourceStrength = source.getValue();

            // 快速距离检查（使用平方距离避免开方）
            long dx = sourcePos.getX() - pos.getX();
            long dy = sourcePos.getY() - pos.getY();
            long dz = sourcePos.getZ() - pos.getZ();
            long distSq = dx*dx + dy*dy + dz*dz;

            int maxRadius = EnvironmentRadiationData.getDynamicRadius(sourceStrength);
            if (distSq > (long) maxRadius * maxRadius) continue;

            // 使用平方距离计算强度（避免开方）
            double distance = Math.sqrt(distSq);
            totalIntensity += (int) (sourceStrength / (distance * distance + 1));

            // 提前退出（如果已经超过上限）
            if (totalIntensity > Integer.MAX_VALUE / 2) break;
        }

        int finalIntensity = (int) Math.min(Integer.MAX_VALUE, totalIntensity);

        if (finalIntensity > 0) {
            int maxSourceStrength = getMaxStrength();
            int cap = (int)(maxSourceStrength * 1.3);
            finalIntensity = Math.min(cap, finalIntensity);
            envData.setRadiationAt(pos, finalIntensity);
        } else {
            envData.setRadiationAt(pos, 0);
        }
    }

    /**
     * 完整重建整个辐射场（使用动态范围）
     */
    public void fullRebuild(Level level) {
        EnvironmentRadiationData envData = EnvironmentRadiationData.get(level);

        if (radiationSources.isEmpty()) {
            envData.clearAll();
            return;
        }

        // 使用 HashSet 收集所有需要更新的位置
        Set<BlockPos> allAffectedPositions = new HashSet<>();
        for (Map.Entry<BlockPos, Integer> source : radiationSources.entrySet()) {
            BlockPos sourcePos = source.getKey();
            int radius = EnvironmentRadiationData.getDynamicRadius(source.getValue());

            // 步长2，减少计算量
            for (int dx = -radius; dx <= radius; dx += 2) {
                for (int dy = -radius; dy <= radius; dy += 2) {
                    for (int dz = -radius; dz <= radius; dz += 2) {
                        BlockPos targetPos = sourcePos.offset(dx, dy, dz);
                        allAffectedPositions.add(targetPos);
                    }
                }
            }
        }

        // 重新计算每个位置
        for (BlockPos pos : allAffectedPositions) {
            recalculateSinglePosition(level, pos, envData);
        }

        envData.setDirty();
    }

    /**
     * 获取缓存的辐射值
     */
    public int getCachedRadiation(BlockPos pos) {
        return radiationCache.getOrDefault(pos, 0);
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        ListTag list = new ListTag();
        radiationSources.forEach((pos, strength) -> {
            CompoundTag entry = new CompoundTag();
            entry.put("pos", NbtUtils.writeBlockPos(pos));
            entry.putInt("strength", strength);
            list.add(entry);
        });
        tag.put("sources", list);
        return tag;
    }

    public static RadiationSourceManager load(CompoundTag tag) {
        RadiationSourceManager manager = new RadiationSourceManager();
        ListTag list = tag.getList("sources", 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            BlockPos pos = NbtUtils.readBlockPos(entry.getCompound("pos"));
            int strength = entry.getInt("strength");
            manager.radiationSources.put(pos, strength);
        }
        return manager;
    }

    public static RadiationSourceManager get(Level level) {
        if (level.isClientSide) {
            return new RadiationSourceManager();
        }
        ServerLevel serverLevel = (ServerLevel) level.getServer().overworld();
        return serverLevel.getDataStorage().computeIfAbsent(
                RadiationSourceManager::load,
                RadiationSourceManager::new,
                "radiation_sources"
        );
    }
}