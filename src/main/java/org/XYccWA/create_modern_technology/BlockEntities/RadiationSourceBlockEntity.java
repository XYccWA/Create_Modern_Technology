package org.XYccWA.create_modern_technology.BlockEntities;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.XYccWA.create_modern_technology.Blocks.NuclearWasteBlock;
import org.XYccWA.create_modern_technology.Blocks.RadiationSourceBlock;
import org.XYccWA.create_modern_technology.Radiation.NeutronCrossSectionManager;
import org.XYccWA.create_modern_technology.Radiation.RadiationUpdateThreadManager;
import org.XYccWA.create_modern_technology.World.RadiationSourceManager;

public class RadiationSourceBlockEntity extends BlockEntity {

    // 燃料系统
    private int fuel;
    private final int maxFuel;
    private final int normalConsumption;
    private final int excitedConsumption;

    // 燃料阈值
    private final double excitedThreshold;   // 进入激发态所需的最小燃料百分比
    private final double criticalThreshold;  // 进入临界态所需的最小燃料百分比
    private final double wasteThreshold;     // 转换为核废料的燃料百分比阈值

    // 临界态计时
    private int criticalTimer;
    private int criticalOverheatTimer;
    private static final int CRITICAL_DURATION = 1200;
    private final int criticalOverheatTime;

    // 性能优化
    private RadiationSourceBlock cachedBlock = null;
    private boolean isExploding = false;

    public RadiationSourceBlockEntity(BlockPos pos, BlockState state,
                                      int maxFuel, int normalConsumption, int excitedConsumption,
                                      int criticalOverheatTime,
                                      double excitedThreshold, double criticalThreshold, double wasteThreshold) {
        super(ModernTechnologyBlockEntities.RADIATION_SOURCE_BE.get(), pos, state);
        this.maxFuel = maxFuel;
        this.normalConsumption = normalConsumption;
        this.excitedConsumption = excitedConsumption;
        this.criticalOverheatTime = criticalOverheatTime;
        this.excitedThreshold = excitedThreshold;
        this.criticalThreshold = criticalThreshold;
        this.wasteThreshold = wasteThreshold;
        this.fuel = maxFuel;
        this.criticalTimer = 0;
        this.criticalOverheatTimer = 0;
    }

    public void tick() {
        if (level == null || level.isClientSide) return;
        if (isExploding) return;

        long gameTime = level.getGameTime();

        BlockState state = level.getBlockState(worldPosition);
        if (!(state.getBlock() instanceof RadiationSourceBlock block)) return;

        if (cachedBlock == null) cachedBlock = block;

        // 中子截面检查
        if (gameTime % 20 == 0) {
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
            handleCriticalState();
        } else {
            criticalOverheatTimer = 0;
        }

        // 燃料耗尽转换（根据配置的阈值）
        double fuelPercent = (double) fuel / maxFuel;
        if (fuelPercent <= wasteThreshold && currentState != RadiationSourceBlock.RadiationState.CRITICAL) {
            if (cachedBlock != null && cachedBlock.getNuclearWasteBlock() != null) {
                convertToNuclearWaste();
                return;
            }
        }
    }

    private void handleCriticalState() {
        if (criticalTimer > 0) {
            criticalTimer--;
            criticalOverheatTimer++;

            if (criticalOverheatTimer >= criticalOverheatTime) {
                explode();
                return;
            }
        }

        if (criticalTimer <= 0 && criticalOverheatTimer < criticalOverheatTime) {
            setFuel(0);
        }
    }

    private void checkNeutronCrossSection() {
        if (level == null) return;

        double totalCrossSection = NeutronCrossSectionManager.calculateTotalCrossSection(level, worldPosition);

        BlockState state = level.getBlockState(worldPosition);
        if (state.getBlock() instanceof RadiationSourceBlock block) {
            RadiationSourceBlock.RadiationState currentState = state.getValue(RadiationSourceBlock.STATE);
            int targetStateIndex = NeutronCrossSectionManager.getStateByCrossSection(totalCrossSection);
            RadiationSourceBlock.RadiationState targetState = switch (targetStateIndex) {
                case 1 -> RadiationSourceBlock.RadiationState.EXCITED;
                case 2 -> RadiationSourceBlock.RadiationState.CRITICAL;
                default -> RadiationSourceBlock.RadiationState.NORMAL;
            };

            if (currentState != targetState) {
                double fuelPercent = (double) fuel / maxFuel;

                // 根据燃料阈值检查是否可以切换
                if (targetState == RadiationSourceBlock.RadiationState.EXCITED && fuelPercent < excitedThreshold) return;
                if (targetState == RadiationSourceBlock.RadiationState.CRITICAL && fuelPercent < criticalThreshold) return;

                block.setState(level, worldPosition, targetState);
            }
        }
    }

    private void explode() {
        if (level == null || isExploding) return;
        isExploding = true;

        RadiationSourceManager.get(level).removeSource(worldPosition);
        RadiationUpdateThreadManager.queueRadiationUpdate(worldPosition, false, 0);

        Block explosionResult = null;
        if (cachedBlock != null) {
            explosionResult = cachedBlock.getExplosionResultBlock();
        }

        float explosionRadius = 5.0f;
        level.explode(null, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5,
                explosionRadius, Level.ExplosionInteraction.TNT);

        level.playSound(null, worldPosition, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 2.0f, 1.0f);

        if (explosionResult != null) {
            level.setBlock(worldPosition, explosionResult.defaultBlockState(), 3);

            if (explosionResult instanceof NuclearWasteBlock wasteBlock) {
                RadiationUpdateThreadManager.queueRadiationUpdate(worldPosition, true, wasteBlock.getInitialRadiation());
            } else if (explosionResult instanceof RadiationSourceBlock) {
                int strength = ((RadiationSourceBlock) explosionResult).getCurrentStrength(explosionResult.defaultBlockState());
                RadiationUpdateThreadManager.queueRadiationUpdate(worldPosition, true, strength);
            }
        } else if (cachedBlock != null && cachedBlock.getNuclearWasteBlock() != null) {
            Block nuclearWaste = cachedBlock.getNuclearWasteBlock();
            level.setBlock(worldPosition, nuclearWaste.defaultBlockState(), 3);

            if (nuclearWaste instanceof NuclearWasteBlock wasteBlock) {
                RadiationUpdateThreadManager.queueRadiationUpdate(worldPosition, true, wasteBlock.getInitialRadiation());
            }
        }

        level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class,
                        new net.minecraft.world.phys.AABB(worldPosition).inflate(10.0))
                .forEach(player -> {
                    player.hurt(org.XYccWA.create_modern_technology.Damage.RadiationDamage.cause(level), 10.0f);
                });

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.players().forEach(player -> {
                if (player.blockPosition().distSqr(worldPosition) <= 128 * 128) {
                    RadiationUpdateThreadManager.syncPlayerPosition((ServerPlayer) player);
                }
            });
        }
    }

    private void convertToNuclearWaste() {
        if (level == null || cachedBlock == null) return;

        BlockState state = level.getBlockState(worldPosition);
        if (state.getValue(RadiationSourceBlock.STATE) == RadiationSourceBlock.RadiationState.CRITICAL) {
            return;
        }

        Block nuclearWaste = cachedBlock.getNuclearWasteBlock();
        if (nuclearWaste != null) {
            RadiationSourceManager.get(level).removeSource(worldPosition);
            RadiationUpdateThreadManager.queueRadiationUpdate(worldPosition, false, 0);

            level.setBlock(worldPosition, nuclearWaste.defaultBlockState(), 3);

            if (nuclearWaste instanceof NuclearWasteBlock wasteBlock) {
                RadiationUpdateThreadManager.queueRadiationUpdate(worldPosition, true, wasteBlock.getInitialRadiation());
            }

            level.playSound(null, worldPosition, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 1.0f);
        }
    }

    public void setFuel(int amount) {
        this.fuel = Math.min(maxFuel, Math.max(0, amount));
        setChanged();

        if (level != null && !level.isClientSide && cachedBlock != null) {
            BlockState state = level.getBlockState(worldPosition);
            int currentStrength = cachedBlock.getCurrentStrength(state);
            float fuelRatio = (float) fuel / maxFuel;
            int adjustedStrength = (int)(currentStrength * fuelRatio);

            if (adjustedStrength > 0) {
                RadiationSourceManager.get(level).addSource(worldPosition, adjustedStrength);
            } else {
                RadiationSourceManager.get(level).removeSource(worldPosition);
            }
            RadiationUpdateThreadManager.queueRadiationUpdate(worldPosition, adjustedStrength > 0, adjustedStrength);
        }
    }

    public void addFuel(int amount) {
        setFuel(fuel + amount);
    }

    public void startCriticalTimer() {
        this.criticalTimer = CRITICAL_DURATION;
        this.criticalOverheatTimer = 0;
        setChanged();
    }

    public int getFuel() { return fuel; }
    public int getMaxFuel() { return maxFuel; }
    public int getCriticalOverheatTimer() { return criticalOverheatTimer; }
    public int getCriticalOverheatTime() { return criticalOverheatTime; }
    public double getFuelPercent() { return (double) fuel / maxFuel; }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("fuel", fuel);
        tag.putInt("criticalTimer", criticalTimer);
        tag.putInt("criticalOverheatTimer", criticalOverheatTimer);
        tag.putBoolean("isExploding", isExploding);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.fuel = tag.getInt("fuel");
        this.criticalTimer = tag.getInt("criticalTimer");
        this.criticalOverheatTimer = tag.getInt("criticalOverheatTimer");
        this.isExploding = tag.getBoolean("isExploding");
    }
}