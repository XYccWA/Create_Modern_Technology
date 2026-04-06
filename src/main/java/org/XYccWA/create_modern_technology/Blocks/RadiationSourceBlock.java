package org.XYccWA.create_modern_technology.Blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.XYccWA.create_modern_technology.World.RadiationSourceManager;

public class RadiationSourceBlock extends Block {

    public static final IntegerProperty RADIATION_STRENGTH = IntegerProperty.create("radiation_strength", 0, 10000);

    private final int baseStrength;

    public RadiationSourceBlock(Properties properties) {
        this(properties, 100);
    }

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
            int strength = state.getValue(RADIATION_STRENGTH);

            // 添加到辐射源管理器
            RadiationSourceManager.get(level).addSource(pos, strength);

            // 增量更新受影响区域
            RadiationSourceManager.get(level).updateAffectedArea(level, pos, true);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        super.onRemove(state, level, pos, newState, isMoving);
        if (!level.isClientSide && !state.is(newState.getBlock())) {

            // 从辐射源管理器移除
            RadiationSourceManager.get(level).removeSource(pos);

            // 增量更新受影响区域
            RadiationSourceManager.get(level).updateAffectedArea(level, pos, false);
        }
    }

    /**
     * 获取方块的辐射强度
     */
    public int getRadiationStrength(BlockState state) {
        return state.getValue(RADIATION_STRENGTH);
    }
}