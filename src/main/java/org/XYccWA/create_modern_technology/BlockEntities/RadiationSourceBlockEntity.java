package org.XYccWA.create_modern_technology.BlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.XYccWA.create_modern_technology.Blocks.RadiationSourceBlock;
import org.XYccWA.create_modern_technology.Radiation.NeutronCrossSectionManager;
import org.XYccWA.create_modern_technology.World.RadiationSourceManager;

public class RadiationSourceBlockEntity extends BlockEntity {

    // 燃料系统
    private int fuel;
    private final int maxFuel;
    private final int normalConsumption;
    private final int excitedConsumption;

    // 临界态计时
    private int criticalTimer;
    private int criticalOverheatTimer;
    private static final int CRITICAL_DURATION = 1200;
    private final int criticalOverheatTime;

    // 中子截面
    private double lastCrossSection = 0.0;
    private int lastCheckTick = 0;
    private static final int CHECK_INTERVAL = 20;

    // 性能优化
    private RadiationSourceBlock cachedBlock = null;
    private int lastStrengthUpdateTick = 0;
    private static final int STRENGTH_UPDATE_INTERVAL = 40;

    // 标记是否正在爆炸（防止重复处理）
    private boolean isExploding = false;

    public RadiationSourceBlockEntity(BlockPos pos, BlockState state,
                                      int maxFuel, int normalConsumption, int excitedConsumption,
                                      int criticalOverheatTime) {
        super(ModernTechnologyBlockEntities.RADIATION_SOURCE_BE.get(), pos, state);
        this.maxFuel = maxFuel;
        this.normalConsumption = normalConsumption;
        this.excitedConsumption = excitedConsumption;
        this.criticalOverheatTime = criticalOverheatTime;
        this.fuel = maxFuel;
        this.criticalTimer = 0;
        this.criticalOverheatTimer = 0;
    }

    public void tick() {
        if (level == null || level.isClientSide) return;

        // 如果正在爆炸，不再处理其他逻辑
        if (isExploding) return;

        long gameTime = level.getGameTime();

        BlockState state = level.getBlockState(worldPosition);
        if (!(state.getBlock() instanceof RadiationSourceBlock block)) return;

        if (cachedBlock == null) cachedBlock = block;

        if (gameTime % CHECK_INTERVAL == 0) {
            checkNeutronCrossSection();
        }

        RadiationSourceBlock.RadiationState currentState = state.getValue(RadiationSourceBlock.STATE);

        // 处理燃料消耗
        if (fuel > 0 && gameTime % 20 == 0) {
            int consumption = switch (currentState) {
                case NORMAL -> normalConsumption;
                case EXCITED -> excitedConsumption;
                case CRITICAL -> 0;
            };

            if (consumption > 0) {
                int newFuel = Math.max(0, fuel - consumption);
                setFuel(newFuel);
            }
        }

        // 处理临界态逻辑
        if (currentState == RadiationSourceBlock.RadiationState.CRITICAL) {
            handleCriticalState(gameTime);
        } else {
            criticalOverheatTimer = 0;
        }

        // 定期更新辐射强度
        if (gameTime - lastStrengthUpdateTick >= STRENGTH_UPDATE_INTERVAL) {
            updateRadiationStrength();
            lastStrengthUpdateTick = (int) gameTime;
        }
    }

    /**
     * 处理临界态逻辑
     */
    private void handleCriticalState(long gameTime) {
        if (criticalTimer > 0) {
            criticalTimer--;

            criticalOverheatTimer++;

            // 检查是否超时爆炸
            if (criticalOverheatTimer >= criticalOverheatTime) {
                explode();
            }
        }
    }

    /**
     * 临界态超时爆炸
     */
    private void explode() {
        if (level == null || isExploding) return;
        isExploding = true;

        // 移除当前辐射源
        RadiationSourceManager.get(level).removeSource(worldPosition);

        // 爆炸效果
        float explosionRadius = 5.0f;
        level.explode(null, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5,
                explosionRadius, Level.ExplosionInteraction.TNT);

        level.playSound(null, worldPosition, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 2.0f, 1.0f);

        // 给附近玩家造成辐射伤害
        level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class,
                        new net.minecraft.world.phys.AABB(worldPosition).inflate(10.0))
                .forEach(player -> {
                    player.hurt(org.XYccWA.create_modern_technology.Damage.RadiationDamage.cause(level), 10.0f);
                });
    }

    /**
     * 检查周围中子截面并自动切换状态
     */
    private void checkNeutronCrossSection() {
        if(level == null || level.getBlockState(worldPosition).isAir()) return;

        double totalCrossSection = NeutronCrossSectionManager.calculateTotalCrossSection(level, worldPosition);

        if (Math.abs(totalCrossSection - lastCrossSection) < 0.01) return;

        lastCrossSection = totalCrossSection;
        int targetStateIndex = NeutronCrossSectionManager.getStateByCrossSection(totalCrossSection);

        BlockState state = level.getBlockState(worldPosition);
        if (state.getBlock() instanceof RadiationSourceBlock block) {
            RadiationSourceBlock.RadiationState currentState = state.getValue(RadiationSourceBlock.STATE);
            RadiationSourceBlock.RadiationState targetState = switch (targetStateIndex) {
                case 1 -> RadiationSourceBlock.RadiationState.EXCITED;
                case 2 -> RadiationSourceBlock.RadiationState.CRITICAL;
                default -> RadiationSourceBlock.RadiationState.NORMAL;
            };

            if (currentState != targetState) {
                if (targetState == RadiationSourceBlock.RadiationState.EXCITED && fuel < 100) return;
                if (targetState == RadiationSourceBlock.RadiationState.CRITICAL && fuel < 1000) return;

                block.setState(level, worldPosition, targetState);
            }
        }

        lastCheckTick = (int) level.getGameTime();
    }

    /**
     * 转换核废料（仅在非临界态时调用）
     */
    private void convertToNuclearWaste() {
        if (level == null || cachedBlock == null) return;

        // 额外安全检查：再次确认不是临界态
        BlockState state = level.getBlockState(worldPosition);
        if (state.getValue(RadiationSourceBlock.STATE) == RadiationSourceBlock.RadiationState.CRITICAL) {
            return;  // 临界态时不转换
        }

        Block nuclearWaste = cachedBlock.getNuclearWasteBlock();
        if (nuclearWaste != null) {
            RadiationSourceManager.get(level).removeSource(worldPosition);
            level.setBlock(worldPosition, nuclearWaste.defaultBlockState(), 3);

            level.playSound(null, worldPosition, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 1.0f);
        }
    }

    /**
     * 更新辐射强度
     */
    private void updateRadiationStrength() {
        if (level == null || cachedBlock == null) return;

        BlockState state = level.getBlockState(worldPosition);
        int currentStrength = cachedBlock.getCurrentStrength(state);
        float fuelRatio = (float) fuel / maxFuel;
        int adjustedStrength = (int)(currentStrength * fuelRatio);

        if (adjustedStrength > 0) {
            RadiationSourceManager.get(level).addSource(worldPosition, adjustedStrength);
        } else {
            RadiationSourceManager.get(level).removeSource(worldPosition);
        }
        RadiationSourceManager.get(level).updateAffectedAreaIncremental(level, worldPosition, adjustedStrength > 0);
    }

    public void startCriticalTimer() {
        this.criticalTimer = CRITICAL_DURATION;
        this.criticalOverheatTimer = 0;
        setChanged();
    }

    public void setFuel(int amount) {
        this.fuel = Math.min(maxFuel, Math.max(0, amount));
        setChanged();
    }

    public void addFuel(int amount) {
        setFuel(fuel + amount);
    }

    public int getFuel() { return fuel; }
    public int getMaxFuel() { return maxFuel; }
    public double getCurrentCrossSection() { return lastCrossSection; }
    public int getCriticalOverheatTimer() { return criticalOverheatTimer; }
    public int getCriticalOverheatTime() { return criticalOverheatTime; }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("fuel", fuel);
        tag.putInt("criticalTimer", criticalTimer);
        tag.putInt("criticalOverheatTimer", criticalOverheatTimer);
        tag.putDouble("lastCrossSection", lastCrossSection);
        tag.putBoolean("isExploding", isExploding);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.fuel = tag.getInt("fuel");
        this.criticalTimer = tag.getInt("criticalTimer");
        this.criticalOverheatTimer = tag.getInt("criticalOverheatTimer");
        this.lastCrossSection = tag.getDouble("lastCrossSection");
        this.isExploding = tag.getBoolean("isExploding");
    }
}