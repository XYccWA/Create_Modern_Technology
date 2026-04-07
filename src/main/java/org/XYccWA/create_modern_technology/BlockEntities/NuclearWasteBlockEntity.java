package org.XYccWA.create_modern_technology.BlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.XYccWA.create_modern_technology.World.RadiationSourceManager;

public class NuclearWasteBlockEntity extends BlockEntity {

    private int currentRadiation;           // 当前辐射强度
    private final int initialRadiation;     // 初始辐射强度
    private final double decayRatePerDay;   // 每天衰变速率
    private final Block decayTarget;        // 衰变完成后的方块

    private long lastDecayDay;              // 上次衰变时的游戏天数
    private static final int TICKS_PER_DAY = 24000; // Minecraft 一天 = 24000 tick

    public NuclearWasteBlockEntity(BlockPos pos, BlockState state, int radiationStrength, double decayRatePerDay, Block decayTarget) {
        super(ModernTechnologyBlockEntities.NUCLEAR_WASTE_BE.get(), pos, state);
        this.initialRadiation = radiationStrength;
        this.currentRadiation = radiationStrength;
        this.decayRatePerDay = decayRatePerDay;
        this.decayTarget = decayTarget;
        this.lastDecayDay = -1;
    }

    public void tick() {
        if (level == null || level.isClientSide) return;

        ServerLevel serverLevel = (ServerLevel) level;
        long currentDay = serverLevel.getDayTime() / TICKS_PER_DAY;

        // 初始化上次衰变天数
        if (lastDecayDay == -1) {
            lastDecayDay = currentDay;
            return;
        }

        // 检查是否过了一天
        if (currentDay > lastDecayDay) {
            long daysPassed = currentDay - lastDecayDay;
            applyDecay(daysPassed);
            lastDecayDay = currentDay;
            setChanged();
        }
    }

    private void applyDecay(long daysPassed) {
        if (currentRadiation <= 0) return;

        // 应用指数衰变: new = current * (1 - decayRate)^days
        double remaining = currentRadiation;
        for (int i = 0; i < daysPassed; i++) {
            remaining = remaining * (1.0 - decayRatePerDay);
        }

        int newRadiation = (int) remaining;

        if (newRadiation <= 0) {
            // 衰变完成，替换方块
            completeDecay();
        } else {
            currentRadiation = Math.max(0, newRadiation);
            updateRadiationField();
        }
    }

    private void completeDecay() {
        if (level == null) return;

        // 移除当前辐射源
        RadiationSourceManager.get(level).removeSource(worldPosition);

        // 替换方块
        if (decayTarget != null) {
            level.setBlock(worldPosition, decayTarget.defaultBlockState(), 3);
        } else {
            level.removeBlock(worldPosition, false);
        }
    }

    private void updateRadiationField() {
        if (level == null) return;

        if (currentRadiation <= 0) {
            RadiationSourceManager.get(level).removeSource(worldPosition);
        } else {
            RadiationSourceManager.get(level).addSource(worldPosition, currentRadiation);
        }
        RadiationSourceManager.get(level).updateAffectedArea(level, worldPosition, currentRadiation > 0);
    }

    public int getCurrentRadiation() {
        return currentRadiation;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("currentRadiation", currentRadiation);
        tag.putLong("lastDecayDay", lastDecayDay);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.currentRadiation = tag.getInt("currentRadiation");
        this.lastDecayDay = tag.getLong("lastDecayDay");
    }
}