package org.XYccWA.create_modern_technology.World;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EnvironmentRadiationData extends SavedData {
    private final Map<BlockPos, Integer> radiationMap = new ConcurrentHashMap<>();

    public static final int RADIATION_SOURCE_RADIUS = 16;
    public static final int SOURCE_BASE_STRENGTH = 100000;

    // 动态范围配置
    public static final int MIN_RADIATION_RADIUS = 4;      // 最小辐射范围
    public static final int MAX_RADIATION_RADIUS = 128;    // 最大辐射范围

    public int getRadiationAt(BlockPos pos) {
        return radiationMap.getOrDefault(pos, 0);
    }

    public void setRadiationAt(BlockPos pos, int intensity) {
        if (intensity <= 0) {
            radiationMap.remove(pos);
        } else {
            radiationMap.put(pos, intensity);
        }
        setDirty();
    }

    /**
     * 清空所有环境辐射数据
     */
    public void clearAll() {
        radiationMap.clear();
        setDirty();
    }

    public void removeSource(BlockPos pos) {
        radiationMap.entrySet().removeIf(entry ->
                entry.getKey().distSqr(pos) <= RADIATION_SOURCE_RADIUS * RADIATION_SOURCE_RADIUS
        );
        setDirty();
    }

    /**
     * 根据辐射源强度计算动态辐射范围
     * @param sourceStrength 辐射源强度 (0-100000)
     * @return 辐射范围（格数）
     */
    public static int getDynamicRadius(int sourceStrength) {
        if (sourceStrength <= 0) return 0;

        // 公式: 半径 = floor(sqrt(源强度))
        int radius = (int) Math.sqrt(sourceStrength);

        // 应用最小和最大限制
        radius = Math.max(MIN_RADIATION_RADIUS, radius);
        radius = Math.min(MAX_RADIATION_RADIUS, radius);

        return radius;
    }

    /**
     * 获取某个位置受单个辐射源影响的强度（带动态范围）
     */
    public static int calculateIntensity(int sourceStrength, double distance, int maxRadius) {
        if (distance > maxRadius) return 0;
        return (int) (sourceStrength / (distance * distance + 1));
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        ListTag list = new ListTag();
        radiationMap.forEach((pos, intensity) -> {
            if (intensity > 0) {
                CompoundTag entry = new CompoundTag();
                entry.put("pos", NbtUtils.writeBlockPos(pos));
                entry.putInt("intensity", intensity);
                list.add(entry);
            }
        });
        tag.put("radiations", list);
        return tag;
    }

    public static EnvironmentRadiationData load(CompoundTag tag) {
        EnvironmentRadiationData data = new EnvironmentRadiationData();
        ListTag list = tag.getList("radiations", 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            BlockPos pos = NbtUtils.readBlockPos(entry.getCompound("pos"));
            int intensity = entry.getInt("intensity");
            data.radiationMap.put(pos, intensity);
        }
        return data;
    }

    public static EnvironmentRadiationData get(Level level) {
        if (level.isClientSide) {
            return new EnvironmentRadiationData();
        }

        ServerLevel serverLevel = (ServerLevel) level.getServer().overworld();
        return serverLevel.getDataStorage().computeIfAbsent(
                EnvironmentRadiationData::load,
                EnvironmentRadiationData::new,
                "environment_radiation"
        );
    }
}