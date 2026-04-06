package org.XYccWA.create_modern_technology.World;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RadiationSourceManager extends SavedData {

    // 存储所有辐射源的位置和强度
    private final Map<BlockPos, Integer> radiationSources = new ConcurrentHashMap<>();

    public void addSource(BlockPos pos, int strength) {
        radiationSources.put(pos, strength);
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
     * 增量更新：只更新受影响的区域（变化位置周围半径内的所有位置）
     */
    public void updateAffectedArea(Level level, BlockPos changedPos, boolean isAdding) {
        EnvironmentRadiationData envData = EnvironmentRadiationData.get(level);
        int radius = EnvironmentRadiationData.RADIATION_SOURCE_RADIUS;

        // 只更新变化位置周围半径内的区域
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos targetPos = changedPos.offset(dx, dy, dz);
                    recalculateSinglePosition(level, targetPos, envData);
                }
            }
        }

        // 标记数据已更改
        envData.setDirty();
    }

    /**
     * 重新计算单个位置的辐射强度
     */
    private void recalculateSinglePosition(Level level, BlockPos pos, EnvironmentRadiationData envData) {
        int totalIntensity = 0;
        int radius = EnvironmentRadiationData.RADIATION_SOURCE_RADIUS;

        // 遍历所有辐射源，计算对当前位置的影响
        for (Map.Entry<BlockPos, Integer> source : radiationSources.entrySet()) {
            BlockPos sourcePos = source.getKey();
            int sourceStrength = source.getValue();

            double distance = Math.sqrt(sourcePos.distSqr(pos));
            if (distance <= radius) {
                totalIntensity += (int) (sourceStrength / (distance * distance + 1));
            }
        }

        // 应用叠加上限（最高源强度的1.3倍）
        if (totalIntensity > 0) {
            int maxSourceStrength = getMaxStrength();
            int cap = (int)(maxSourceStrength * 1.3);
            totalIntensity = Math.min(cap, totalIntensity);
            envData.setRadiationAt(pos, totalIntensity);
        } else {
            envData.setRadiationAt(pos, 0);
        }
    }

    /**
     * 完整重建整个辐射场（用于调试或特殊情况）
     */
    public void fullRebuild(Level level) {
        if (radiationSources.isEmpty()) {
            EnvironmentRadiationData.get(level).clearAll();
            return;
        }

        EnvironmentRadiationData envData = EnvironmentRadiationData.get(level);
        int radius = EnvironmentRadiationData.RADIATION_SOURCE_RADIUS;

        // 收集所有需要更新的位置（所有辐射源影响范围的并集）
        Set<BlockPos> allAffectedPositions = new HashSet<>();
        for (BlockPos sourcePos : radiationSources.keySet()) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
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