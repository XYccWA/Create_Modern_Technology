package org.XYccWA.create_modern_technology.BlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.XYccWA.create_modern_technology.Radiation.RadiationUpdateThreadManager;
import org.XYccWA.create_modern_technology.World.RadiationSourceManager;

public class NuclearWasteBlockEntity extends BlockEntity {

    private int currentRadiation;
    private final int initialRadiation;
    private final double decayRatePerDay;
    private final Block decayTarget;

    private long lastDecayDay;
    private static final int TICKS_PER_DAY = 24000;

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

        if (lastDecayDay == -1) {
            lastDecayDay = currentDay;
            return;
        }

        if (currentDay > lastDecayDay) {
            long daysPassed = currentDay - lastDecayDay;
            applyDecay(daysPassed);
            lastDecayDay = currentDay;
            setChanged();
        }
    }

    private void applyDecay(long daysPassed) {
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

        RadiationUpdateThreadManager.queueRadiationUpdate(worldPosition, false, 0);

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

    public int getCurrentRadiation() { return currentRadiation; }

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