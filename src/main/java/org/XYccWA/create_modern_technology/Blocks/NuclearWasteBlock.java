package org.XYccWA.create_modern_technology.Blocks;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.XYccWA.create_modern_technology.BlockEntities.NuclearWasteBlockEntity;
import org.XYccWA.create_modern_technology.World.EnvironmentRadiationData;
import org.XYccWA.create_modern_technology.World.RadiationSourceManager;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class NuclearWasteBlock extends Block implements EntityBlock {

    private final int radiationStrength;      // 初始辐射强度
    private final double decayRatePerDay;     // 每天衰变速率（0-1，如0.1表示每天减少10%）
    private final Block decayTarget;           // 衰变完成后的方块

    public NuclearWasteBlock(Properties properties, int radiationStrength, double decayRatePerDay, Block decayTarget) {
        super(properties);
        this.radiationStrength = Math.min(100000, Math.max(0, radiationStrength));
        this.decayRatePerDay = Math.min(1.0, Math.max(0, decayRatePerDay));
        this.decayTarget = decayTarget;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NuclearWasteBlockEntity(pos, state, radiationStrength, decayRatePerDay, decayTarget);
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
            if (level.getBlockEntity(pos) == null) {
                BlockEntity be = newBlockEntity(pos, state);
                level.setBlockEntity(be);
            }
            // 注册辐射
            RadiationSourceManager.get(level).addSource(pos, getCurrentRadiationStrength(level, pos));
            RadiationSourceManager.get(level).updateAffectedAreaIncremental(level, pos, true);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        super.onRemove(state, level, pos, newState, isMoving);
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            RadiationSourceManager.get(level).removeSource(pos);
            RadiationSourceManager.get(level).updateAffectedAreaIncremental(level, pos, false);
        }
    }

    public int getCurrentRadiationStrength(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof NuclearWasteBlockEntity be) {
            return be.getCurrentRadiation();
        }
        return radiationStrength;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        int radius = EnvironmentRadiationData.getDynamicRadius(radiationStrength);
        int halfLifeDays = (int) Math.ceil(0.693 / decayRatePerDay);

        tooltip.add(Component.literal("§7核废料").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("  §a辐射强度: " + radiationStrength + " / 范围 " + radius + " 格"));
        tooltip.add(Component.literal("  §e衰变速率: " + (int)(decayRatePerDay * 100) + "%/天"));
        tooltip.add(Component.literal("  §7半衰期: 约 " + halfLifeDays + " 天"));
        if (decayTarget != null) {
            tooltip.add(Component.literal("  §7衰变产物: " + decayTarget.getName().getString()));
        }
    }
}