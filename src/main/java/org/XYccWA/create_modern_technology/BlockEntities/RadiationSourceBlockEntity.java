package org.XYccWA.create_modern_technology.BlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.XYccWA.create_modern_technology.Blocks.RadiationSourceBlock;
import org.XYccWA.create_modern_technology.World.RadiationSourceManager;

public class RadiationSourceBlockEntity extends BlockEntity {

    private int fuel;                    // 当前燃料量
    private final int maxFuel;           // 最大燃料量
    private final int normalConsumption; // 普通态消耗
    private final int excitedConsumption; // 激发态消耗

    private int criticalTimer;           // 临界态计时器（tick）
    private static final int CRITICAL_DURATION = 1200; // 1分钟 = 1200 tick

    public RadiationSourceBlockEntity(BlockPos pos, BlockState state, int maxFuel, int normalConsumption, int excitedConsumption) {
        super(ModernTechnologyBlockEntities.RADIATION_SOURCE_BE.get(), pos, state);
        this.maxFuel = maxFuel;
        this.normalConsumption = normalConsumption;
        this.excitedConsumption = excitedConsumption;
        this.fuel = maxFuel;  // 初始满燃料
        this.criticalTimer = 0;
    }

// 在 tick() 方法中添加燃料耗尽转换逻辑

    public void tick() {
        if (level == null || level.isClientSide) return;

        BlockState state = level.getBlockState(worldPosition);
        if (!(state.getBlock() instanceof RadiationSourceBlock block)) return;

        RadiationSourceBlock.RadiationState currentState = state.getValue(RadiationSourceBlock.STATE);

        // 处理燃料消耗
        if (fuel > 0) {
            int consumption = switch (currentState) {
                case NORMAL -> normalConsumption;
                case EXCITED -> excitedConsumption;
                case CRITICAL -> 0;
            };

            if (consumption > 0 && level.getGameTime() % 20 == 0) {
                int newFuel = Math.max(0, fuel - consumption);
                setFuel(newFuel);
            }
        }

        // 处理临界态计时
        if (currentState == RadiationSourceBlock.RadiationState.CRITICAL) {
            if (criticalTimer > 0) {
                criticalTimer--;
                if (criticalTimer <= 0) {
                    setFuel(0);
                    block.setState(level, worldPosition, RadiationSourceBlock.RadiationState.NORMAL);
                }
            }
        }

        // 燃料耗尽时转换为核废料
        if (fuel <= 0) {
            convertToNuclearWaste();
            return;
        }

        // 根据燃料量更新辐射强度
        updateRadiationStrength();
    }

    private void convertToNuclearWaste() {
        if (level == null) return;

        // 获取核废料方块（从辐射源方块配置中获取）
        if (level.getBlockState(worldPosition).getBlock() instanceof RadiationSourceBlock block) {
            Block nuclearWaste = block.getNuclearWasteBlock();
            if (nuclearWaste != null) {
                // 移除当前辐射源
                RadiationSourceManager.get(level).removeSource(worldPosition);
                // 替换为核废料
                level.setBlock(worldPosition, nuclearWaste.defaultBlockState(), 3);
            } else {
                // 如果没有配置核废料，变为普通空气
                level.removeBlock(worldPosition, false);
            }
        }
    }
    private void updateRadiationStrength() {
        if (level == null) return;

        BlockState state = level.getBlockState(worldPosition);
        if (state.getBlock() instanceof RadiationSourceBlock block) {
            int currentStrength = block.getCurrentStrength(state);
            // 根据燃料比例降低强度
            float fuelRatio = (float) fuel / maxFuel;
            int adjustedStrength = (int)(currentStrength * fuelRatio);

            // 更新到管理器
            RadiationSourceManager.get(level).addSource(worldPosition, adjustedStrength);
            RadiationSourceManager.get(level).updateAffectedArea(level, worldPosition, true);
        }
    }

    public void startCriticalTimer() {
        this.criticalTimer = CRITICAL_DURATION;
        setChanged();
    }

    public void setFuel(int amount) {
        this.fuel = Math.min(maxFuel, Math.max(0, amount));
        setChanged();
        updateRadiationStrength();
    }

    public void addFuel(int amount) {
        setFuel(fuel + amount);
    }

    public int getFuel() { return fuel; }
    public int getMaxFuel() { return maxFuel; }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("fuel", fuel);
        tag.putInt("criticalTimer", criticalTimer);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.fuel = tag.getInt("fuel");
        this.criticalTimer = tag.getInt("criticalTimer");
    }
}