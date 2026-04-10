package org.XYccWA.create_modern_technology.Blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.XYccWA.create_modern_technology.BlockEntities.NuclearWasteBlockEntity;
import org.XYccWA.create_modern_technology.Radiation.RadiationUpdateThreadManager;
import org.XYccWA.create_modern_technology.World.RadiationSourceManager;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class NuclearWasteBlock extends Block implements EntityBlock {

    private final int initialRadiation;
    private final double decayRatePerDay;
    private final Block decayTarget;

    public NuclearWasteBlock(Properties properties, int radiationStrength, double decayRatePerDay, Block decayTarget) {
        super(properties);
        this.initialRadiation = Math.min(100000, Math.max(0, radiationStrength));
        this.decayRatePerDay = Math.min(1.0, Math.max(0, decayRatePerDay));
        this.decayTarget = decayTarget;
    }

    public int getInitialRadiation() {
        return initialRadiation;
    }

    public double getDecayRatePerDay() {
        return decayRatePerDay;
    }

    public Block getDecayTarget() {
        return decayTarget;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NuclearWasteBlockEntity(pos, state, initialRadiation, decayRatePerDay, decayTarget);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide) {
            return (lvl, pos, st, be) -> {
                if (be instanceof NuclearWasteBlockEntity tile) {
                    tile.tick();
                }
            };
        }
        return null;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide) {
            // 确保 BlockEntity 存在
            if (level.getBlockEntity(pos) == null) {
                BlockEntity be = newBlockEntity(pos, state);
                level.setBlockEntity(be);
            }

            // 关键修复：注册到辐射源管理器
            RadiationSourceManager.get(level).addSource(pos, initialRadiation);

            // 添加到更新队列
            RadiationUpdateThreadManager.queueRadiationUpdate(pos, true, initialRadiation);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        super.onRemove(state, level, pos, newState, isMoving);
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            // 从辐射源管理器移除
            RadiationSourceManager.get(level).removeSource(pos);
            // 添加到更新队列
            RadiationUpdateThreadManager.queueRadiationUpdate(pos, false, 0);
            // 关键修复：立即同步周围玩家的辐射数据
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.players().forEach(player -> {
                    // 如果玩家在影响范围内，立即同步
                    double distance = player.blockPosition().distSqr(pos);
                    if (distance <= 128 * 128) { // 128格范围内
                        RadiationUpdateThreadManager.syncPlayerPosition((ServerPlayer) player);
                    }
                });
            }
        }
    }

    public int getCurrentRadiationStrength(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof NuclearWasteBlockEntity be) {
            return be.getCurrentRadiation();
        }
        return initialRadiation;
    }
}