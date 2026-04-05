package org.XYccWA.create_modern_technology.Blocks;

import org.XYccWA.create_modern_technology.World.EnvironmentRadiationData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

public class RadiationSourceBlock extends Block {

    // 辐射强度属性（0-100，数值越大辐射越强）
    public static final IntegerProperty RADIATION_STRENGTH = IntegerProperty.create("radiation_strength", 0, 10000);

    // 默认基础强度（1格距离时的强度）
    private final int baseStrength;

    // 构造函数 - 使用默认强度 100
    public RadiationSourceBlock(Properties properties) {
        this(properties, 100);
    }

    // 构造函数 - 自定义强度
    public RadiationSourceBlock(Properties properties, int baseStrength) {
        super(properties);
        this.baseStrength = baseStrength;
        this.registerDefaultState(this.stateDefinition.any().setValue(RADIATION_STRENGTH, baseStrength));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RADIATION_STRENGTH);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide) {
            // 获取当前方块的辐射强度
            int strength = state.getValue(RADIATION_STRENGTH);
            updateRadiationField(level, pos, true, strength);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        super.onRemove(state, level, pos, newState, isMoving);
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            EnvironmentRadiationData.get(level).removeSource(pos);
            updateRadiationField(level, pos, false, 0);
        }
    }

    private void updateRadiationField(Level level, BlockPos center, boolean add, int strength) {
        EnvironmentRadiationData data = EnvironmentRadiationData.get(level);
        int radius = EnvironmentRadiationData.RADIATION_SOURCE_RADIUS;

        // 使用方块的强度值，而不是全局常量
        int sourceStrength = strength;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos targetPos = center.offset(dx, dy, dz);
                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

                    if (distance <= radius) {
                        // 强度随距离递减：强度 = 方块强度 / (距离² + 1)
                        int intensity = (int) (sourceStrength / (distance * distance + 1));

                        if (add) {
                            int current = data.getRadiationAt(targetPos);
                            int maxIntensity = Math.max(current, intensity);
                            int weaker = Math.min(current, intensity);
                            int newIntensity = maxIntensity + (int) (weaker * 0.3);
                            newIntensity = Math.min((int) (maxIntensity * 1.3), newIntensity);
                            data.setRadiationAt(targetPos, newIntensity);
                        } else {
                            recalculatePosition(level, targetPos, data);
                        }
                    }
                }
            }
        }
    }

    private void recalculatePosition(Level level, BlockPos pos, EnvironmentRadiationData data) {
        int totalIntensity = 0;
        int radius = EnvironmentRadiationData.RADIATION_SOURCE_RADIUS;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos sourcePos = pos.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(sourcePos);
                    if (state.getBlock() instanceof RadiationSourceBlock sourceBlock) {
                        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (distance <= radius) {
                            int sourceStrength = state.getValue(RADIATION_STRENGTH);
                            totalIntensity += (int) (sourceStrength / (distance * distance + 1));
                        }
                    }
                }
            }
        }

        if (totalIntensity > 0) {
            data.setRadiationAt(pos, totalIntensity);
        } else {
            data.setRadiationAt(pos, 0);
        }
    }
}