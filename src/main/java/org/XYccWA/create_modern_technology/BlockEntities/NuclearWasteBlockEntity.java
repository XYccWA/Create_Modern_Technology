package org.XYccWA.create_modern_technology.BlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.XYccWA.create_modern_technology.Blocks.NuclearWasteBlock;
import org.XYccWA.create_modern_technology.Radiation.RadiationUpdateThreadManager;
import org.XYccWA.create_modern_technology.World.RadiationSourceManager;

public class NuclearWasteBlockEntity extends BlockEntity {

    private int currentRadiation;
    private final int initialRadiation;
    private final double decayRatePerDay;
    private final Block decayTarget;

    private long lastDecayDay;
    private static final int TICKS_PER_DAY = 24000;

    // 缓存方块引用
    private NuclearWasteBlock cachedBlock = null;

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

        // 初始化缓存
        if (cachedBlock == null && level.getBlockState(worldPosition).getBlock() instanceof NuclearWasteBlock block) {
            cachedBlock = block;
        }

        ServerLevel serverLevel = (ServerLevel) level;
        long currentDay = serverLevel.getDayTime() / TICKS_PER_DAY;

        if (lastDecayDay == -1) {
            lastDecayDay = currentDay;
            // 首次放置时确保注册到辐射源管理器
            registerRadiationSource();
            return;
        }

        if (currentDay > lastDecayDay) {
            long daysPassed = currentDay - lastDecayDay;
            applyDecay(daysPassed);
            lastDecayDay = currentDay;
            setChanged();
        }
    }

    private void registerRadiationSource() {
        if (level == null) return;

        if (currentRadiation > 0) {
            RadiationSourceManager.get(level).addSource(worldPosition, currentRadiation);
            RadiationUpdateThreadManager.queueRadiationUpdate(worldPosition, true, currentRadiation);
        }
    }

    private void applyDecay(long daysPassed) {
        if (level == null) return;
        if (currentRadiation <= 0) return;

        double remaining = currentRadiation;
        for (int i = 0; i < daysPassed; i++) {
            remaining = remaining * (1.0 - decayRatePerDay);
        }

        int newRadiation = (int) remaining;

        if (newRadiation <= 0) {
            completeDecay();
        } else {
            currentRadiation = newRadiation;
            updateRadiationField();
        }
    }

    private void completeDecay() {
        if (level == null) return;

        // 移除当前辐射源
        RadiationSourceManager.get(level).removeSource(worldPosition);
        RadiationUpdateThreadManager.queueRadiationUpdate(worldPosition, false, 0);

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
            RadiationUpdateThreadManager.queueRadiationUpdate(worldPosition, false, 0);
        } else {
            RadiationSourceManager.get(level).addSource(worldPosition, currentRadiation);
            RadiationUpdateThreadManager.queueRadiationUpdate(worldPosition, true, currentRadiation);
        }
    }

    public int getCurrentRadiation() {
        return currentRadiation;
    }

    public int getInitialRadiation() {
        return initialRadiation;
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