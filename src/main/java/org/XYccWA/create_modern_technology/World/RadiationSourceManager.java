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

    private final Map<BlockPos, Integer> radiationSources = new ConcurrentHashMap<>();

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
     * 增量更新：只更新受影响的区域
     * @param level 世界
     * @param changedPos 变化的位置
     * @param isAdding true=添加辐射源, false=移除辐射源
     */
    public void updateAffectedArea(Level level, BlockPos changedPos, boolean isAdding) {
        EnvironmentRadiationData envData = EnvironmentRadiationData.get(level);

        if (!hasSources()) {
            // 没有辐射源了，清空所有环境辐射数据
            envData.clearAll();
            return;
        }

        // 获取当前所有辐射源的最大强度（用于计算更新范围）
        int maxStrength = getMaxStrength();
        int maxRadius = EnvironmentRadiationData.getDynamicRadius(maxStrength);

        // 如果是移除操作，需要额外扩大更新范围（因为要清除原有辐射数据）
        int updateRadius = maxRadius;
        if (!isAdding) {
            // 移除时，需要覆盖原辐射源可能的最大范围（假设原强度可能很高）
            // 使用全局最大范围 128 确保完全清除
            updateRadius = Math.max(maxRadius, 128);
        }

        // 更新受影响区域
        for (int dx = -updateRadius; dx <= updateRadius; dx++) {
            for (int dy = -updateRadius; dy <= updateRadius; dy++) {
                for (int dz = -updateRadius; dz <= updateRadius; dz++) {
                    BlockPos targetPos = changedPos.offset(dx, dy, dz);
                    recalculateSinglePosition(level, targetPos, envData);
                }
            }
        }

        envData.setDirty();
    }

    /**
     * 重新计算单个位置的辐射强度（考虑所有辐射源及其动态范围）
     */
    private void recalculateSinglePosition(Level level, BlockPos pos, EnvironmentRadiationData envData) {
        if (!hasSources()) {
            envData.setRadiationAt(pos, 0);
            return;
        }

        long totalIntensity = 0;

        // 遍历所有辐射源
        for (Map.Entry<BlockPos, Integer> source : radiationSources.entrySet()) {
            BlockPos sourcePos = source.getKey();
            int sourceStrength = source.getValue();

            double distance = Math.sqrt(sourcePos.distSqr(pos));
            int maxRadius = EnvironmentRadiationData.getDynamicRadius(sourceStrength);

            // 超出该辐射源的范围则跳过
            if (distance > maxRadius) continue;

            totalIntensity += (int) (sourceStrength / (distance * distance + 1));
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

        Set<BlockPos> allAffectedPositions = new HashSet<>();
        for (Map.Entry<BlockPos, Integer> source : radiationSources.entrySet()) {
            BlockPos sourcePos = source.getKey();
            int radius = EnvironmentRadiationData.getDynamicRadius(source.getValue());

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        BlockPos targetPos = sourcePos.offset(dx, dy, dz);
                        allAffectedPositions.add(targetPos);
                    }
                }
            }
        }

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