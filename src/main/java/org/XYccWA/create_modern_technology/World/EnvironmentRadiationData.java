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

    public static final int RADIATION_SOURCE_RADIUS = 12;  // 减小半径提高性能
    public static final int SOURCE_BASE_STRENGTH = 100;

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
     * 清除指定位置及其影响范围内的所有辐射数据
     */
    public void clearRadiationAround(BlockPos center, int radius) {
        int radiusSq = radius * radius;
        radiationMap.entrySet().removeIf(entry -> {
            BlockPos pos = entry.getKey();
            return pos.distSqr(center) <= radiusSq;
        });
        setDirty();
    }

    /**
     * 完全清除所有辐射数据（调试用）
     */
    public void clearAll() {
        radiationMap.clear();
        setDirty();
    }

    /**
     * 获取指定位置是否有辐射
     */
    public boolean hasRadiationAt(BlockPos pos) {
        return radiationMap.containsKey(pos);
    }

    public void removeSource(BlockPos pos) {
        radiationMap.entrySet().removeIf(entry ->
                entry.getKey().distSqr(pos) <= RADIATION_SOURCE_RADIUS * RADIATION_SOURCE_RADIUS
        );
        setDirty();
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
            if (intensity > 0) {
                data.radiationMap.put(pos, intensity);
            }
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