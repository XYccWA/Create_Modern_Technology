package org.XYccWA.create_modern_technology.Blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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

public class NuclearWasteBlock extends Block implements EntityBlock {

    // NBT 键名
    public static final String NBT_CURRENT_RADIATION = "waste_current_radiation";
    public static final String NBT_LAST_DECAY_DAY = "waste_last_decay_day";

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
            if (level.getBlockEntity(pos) == null) {
                BlockEntity be = newBlockEntity(pos, state);
                level.setBlockEntity(be);
            }
            RadiationSourceManager.get(level).addSource(pos, initialRadiation);
            RadiationUpdateThreadManager.queueRadiationUpdate(pos, true, initialRadiation);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        super.onRemove(state, level, pos, newState, isMoving);
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            RadiationSourceManager.get(level).removeSource(pos);
            RadiationUpdateThreadManager.queueRadiationUpdate(pos, false, 0);
        }
    }

    /**
     * 挖掘时保存 NBT 到掉落物
     */
    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool) {
        if (!level.isClientSide && blockEntity instanceof NuclearWasteBlockEntity tile) {
            ItemStack dropStack = new ItemStack(this.asItem());
            CompoundTag tag = new CompoundTag();
            tag.putInt(NBT_CURRENT_RADIATION, tile.getCurrentRadiation());
            tag.putLong(NBT_LAST_DECAY_DAY, tile.getLastDecayDay());
            dropStack.setTag(tag);

            popResource(level, pos, dropStack);
            level.removeBlock(pos, false);
            return;
        }

        super.playerDestroy(level, player, pos, state, blockEntity, tool);
    }

    /**
     * 放置时从物品 NBT 恢复数据
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);

        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof NuclearWasteBlockEntity tile && stack.hasTag()) {
                CompoundTag tag = stack.getTag();
                if (tag.contains(NBT_CURRENT_RADIATION)) {
                    tile.setCurrentRadiation(tag.getInt(NBT_CURRENT_RADIATION));
                }
                if (tag.contains(NBT_LAST_DECAY_DAY)) {
                    tile.setLastDecayDay(tag.getLong(NBT_LAST_DECAY_DAY));
                }
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