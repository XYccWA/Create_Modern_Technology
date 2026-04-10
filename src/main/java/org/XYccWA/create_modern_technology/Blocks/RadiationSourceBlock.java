package org.XYccWA.create_modern_technology.Blocks;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.util.StringRepresentable;
import org.XYccWA.create_modern_technology.BlockEntities.RadiationSourceBlockEntity;
import org.XYccWA.create_modern_technology.Radiation.RadiationUpdateThreadManager;
import org.XYccWA.create_modern_technology.World.EnvironmentRadiationData;
import org.XYccWA.create_modern_technology.World.RadiationSourceManager;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class RadiationSourceBlock extends Block implements EntityBlock {

    // 辐射状态枚举
    public static enum RadiationState implements StringRepresentable {
        NORMAL("normal"),
        EXCITED("excited"),
        CRITICAL("critical");

        private final String name;

        private RadiationState(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() { return name; }
    }

    public static final EnumProperty<RadiationState> STATE = EnumProperty.create("state", RadiationState.class);

    // 三态辐射强度配置
    private final int normalStrength;
    private final int excitedStrength;
    private final int criticalStrength;

    // 燃料配置
    private final int maxFuel;
    private final int normalConsumption;
    private final int excitedConsumption;

    // 核废料配置
    private final Block nuclearWasteBlock;

    // 临界态超时时间（tick）
    private final int criticalOverheatTime;

    /**
     * 完整构造函数
     */
    public RadiationSourceBlock(Properties properties,
                                int normalStrength, int excitedStrength, int criticalStrength,
                                int maxFuel, int normalConsumption, int excitedConsumption,
                                Block nuclearWasteBlock, int criticalOverheatTime) {
        super(properties);
        this.normalStrength = Math.min(100000, Math.max(0, normalStrength));
        this.excitedStrength = Math.min(100000, Math.max(0, excitedStrength));
        this.criticalStrength = Math.min(100000, Math.max(0, criticalStrength));
        this.maxFuel = Math.max(0, maxFuel);
        this.normalConsumption = Math.max(0, normalConsumption);
        this.excitedConsumption = Math.max(0, excitedConsumption);
        this.nuclearWasteBlock = nuclearWasteBlock;
        this.criticalOverheatTime = Math.max(20, criticalOverheatTime);  // 至少1秒
        this.registerDefaultState(this.stateDefinition.any().setValue(STATE, RadiationState.NORMAL));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STATE);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RadiationSourceBlockEntity(pos, state, maxFuel, normalConsumption, excitedConsumption, criticalOverheatTime);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide) {
            return (lvl, pos, st, be) -> {
                if (be instanceof RadiationSourceBlockEntity tile) {
                    tile.tick();
                }
            };
        }
        return null;
    }

    // 修改 onPlace 方法
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide) {
            // 初始化后台线程管理器
            RadiationUpdateThreadManager.setServerLevel(level);

            if (level.getBlockEntity(pos) == null) {
                BlockEntity be = newBlockEntity(pos, state);
                level.setBlockEntity(be);
            }
            int strength = getCurrentStrength(state);
            RadiationSourceManager.get(level).addSource(pos, strength);
            RadiationUpdateThreadManager.queueRadiationUpdate(pos, true, strength);
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

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            cycleState(level, pos, state);
        }
        return InteractionResult.SUCCESS;
    }

    public void cycleState(Level level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof RadiationSourceBlockEntity tile) {
            RadiationState current = state.getValue(STATE);
            RadiationState next;

            switch (current) {
                case NORMAL -> {
                    if (tile.getFuel() >= 100) {
                        next = RadiationState.EXCITED;
                    } else {
                        if (level.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), 10, false) != null) {
                            level.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), 10, false)
                                    .displayClientMessage(Component.literal("§c燃料不足，无法进入激发态"), true);
                        }
                        return;
                    }
                }
                case EXCITED -> {
                    if (tile.getFuel() >= 1000) {
                        next = RadiationState.CRITICAL;
                    } else {
                        if (level.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), 10, false) != null) {
                            level.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), 10, false)
                                    .displayClientMessage(Component.literal("§c燃料不足，无法进入临界态"), true);
                        }
                        return;
                    }
                }
                case CRITICAL -> next = RadiationState.NORMAL;
                default -> next = RadiationState.NORMAL;
            }

            setState(level, pos, next);
        }
    }

    public void setState(Level level, BlockPos pos, RadiationState targetState) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof RadiationSourceBlock && state.getValue(STATE) != targetState) {
            BlockState newState = state.setValue(STATE, targetState);
            level.setBlock(pos, newState, 3);

            if (targetState == RadiationState.CRITICAL && level.getBlockEntity(pos) instanceof RadiationSourceBlockEntity tile) {
                tile.startCriticalTimer();
            }

            int newStrength = getCurrentStrength(newState);
            RadiationSourceManager manager = RadiationSourceManager.get(level);
            manager.addSource(pos, newStrength);
            manager.updateAffectedArea(level, pos, true);
        }
    }

    public int getCurrentStrength(BlockState state) {
        RadiationState currentState = state.getValue(STATE);
        return switch (currentState) {
            case NORMAL -> normalStrength;
            case EXCITED -> excitedStrength;
            case CRITICAL -> criticalStrength;
        };
    }

    public int getNormalStrength() { return normalStrength; }
    public int getExcitedStrength() { return excitedStrength; }
    public int getCriticalStrength() { return criticalStrength; }
    public int getMaxFuel() { return maxFuel; }
    public Block getNuclearWasteBlock() { return nuclearWasteBlock; }
    public int getCriticalOverheatTime() { return criticalOverheatTime; }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        int normalRadius = EnvironmentRadiationData.getDynamicRadius(normalStrength);
        int excitedRadius = EnvironmentRadiationData.getDynamicRadius(excitedStrength);
        int criticalRadius = EnvironmentRadiationData.getDynamicRadius(criticalStrength);
        int overheatSeconds = criticalOverheatTime / 20;

        tooltip.add(Component.literal("§7三态辐射源").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("  §a普通态§r: " + normalStrength + " 强度 / " + normalRadius + " 格范围"));
        tooltip.add(Component.literal("  §6激发态§r: " + excitedStrength + " 强度 / " + excitedRadius + " 格范围"));
        tooltip.add(Component.literal("  §c临界态§r: " + criticalStrength + " 强度 / " + criticalRadius + " 格范围"));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("§7燃料容量: " + maxFuel));
        tooltip.add(Component.literal("§7消耗速率: 普通态 " + normalConsumption + "/秒, 激发态 " + excitedConsumption + "/秒"));
        tooltip.add(Component.literal("§c临界态超时: " + overheatSeconds + " 秒后爆炸"));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("§7中子反应特性:").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("  §7周围6个方块的中子截面 ≥ 0.8 → 激发态"));
        tooltip.add(Component.literal("  §7周围6个方块的中子截面 ≥ 1.0 → 临界态"));
    }
}