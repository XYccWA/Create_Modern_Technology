package org.XYccWA.create_modern_technology.Blocks;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.util.StringRepresentable;
import org.XYccWA.create_modern_technology.World.EnvironmentRadiationData;
import org.XYccWA.create_modern_technology.World.RadiationSourceManager;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class RadiationSourceBlock extends Block {

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

    // 半衰期相关属性
    public static final IntegerProperty DECAY_LEVEL = IntegerProperty.create("decay_level", 0, 100);  // 衰变等级 0-100
    public static final BooleanProperty DECAY_ENABLED = BooleanProperty.create("decay_enabled");      // 是否启用半衰期

    // 三态基础强度配置
    private final int normalStrength;
    private final int excitedStrength;
    private final int criticalStrength;

    // 半衰期配置（单位：游戏刻，20刻 = 1秒）
    private final int halfLifeTicks;      // 半衰期刻数
    private final int decayStartDelay;    // 开始衰减延迟（刻）

    /**
     * 完整构造函数 - 支持半衰期
     * @param properties 方块属性
     * @param normalStrength 普通态强度
     * @param excitedStrength 激发态强度
     * @param criticalStrength 临界态强度
     * @param halfLifeSeconds 半衰期（秒）
     * @param decayStartSeconds 开始衰减延迟（秒）
     */
    public RadiationSourceBlock(Properties properties, int normalStrength, int excitedStrength, int criticalStrength,
                                int halfLifeSeconds, int decayStartSeconds) {
        super(properties);
        this.normalStrength = Math.min(100000, Math.max(0, normalStrength));
        this.excitedStrength = Math.min(100000, Math.max(0, excitedStrength));
        this.criticalStrength = Math.min(100000, Math.max(0, criticalStrength));
        this.halfLifeTicks = halfLifeSeconds * 20;
        this.decayStartDelay = decayStartSeconds * 20;

        this.registerDefaultState(this.stateDefinition.any()
                .setValue(STATE, RadiationState.NORMAL)
                .setValue(DECAY_LEVEL, 0)
                .setValue(DECAY_ENABLED, halfLifeSeconds > 0));
    }

    /**
     * 构造函数 - 无半衰期
     */
    public RadiationSourceBlock(Properties properties, int normalStrength, int excitedStrength, int criticalStrength) {
        this(properties, normalStrength, excitedStrength, criticalStrength, 0, 0);
    }

    /**
     * 构造函数 - 默认强度
     */
    public RadiationSourceBlock(Properties properties) {
        this(properties, 100, 1000, 10000, 0, 0);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STATE, DECAY_LEVEL, DECAY_ENABLED);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide) {
            int strength = getCurrentStrength(state);
            RadiationSourceManager.get(level).addSource(pos, strength);
            RadiationSourceManager.get(level).updateAffectedArea(level, pos, true);

            // 如果启用了半衰期，调度第一次衰变
            if (state.getValue(DECAY_ENABLED) && level instanceof ServerLevel serverLevel) {
                scheduleDecay(serverLevel, pos, state);
            }
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        super.onRemove(state, level, pos, newState, isMoving);
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            RadiationSourceManager.get(level).removeSource(pos);
            RadiationSourceManager.get(level).updateAffectedArea(level, pos, false);
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            // 手持钟时显示衰变信息
            ItemStack heldItem = player.getItemInHand(hand);
            if (heldItem.getItem().toString().contains("clock")) {
                int decayLevel = state.getValue(DECAY_LEVEL);
                double remainingPercent = getRemainingStrengthPercent(decayLevel);
                player.displayClientMessage(
                        Component.literal("§e辐射源衰变进度: " + decayLevel + "% / 剩余强度: " + String.format("%.1f", remainingPercent) + "%"),
                        true
                );
                return InteractionResult.SUCCESS;
            }

            // 手持特定物品重置衰变（调试用）
            if (heldItem.getItem().toString().contains("diamond")) {
                resetDecay(level, pos, state);
                player.displayClientMessage(Component.literal("§a已重置辐射源衰变"), true);
                return InteractionResult.SUCCESS;
            }

            cycleState(level, pos, state);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * 循环切换状态：普通 → 激发 → 临界 → 普通
     */
    public void cycleState(Level level, BlockPos pos, BlockState state) {
        RadiationState current = state.getValue(STATE);
        RadiationState next;

        switch (current) {
            case NORMAL -> next = RadiationState.EXCITED;
            case EXCITED -> next = RadiationState.CRITICAL;
            case CRITICAL -> next = RadiationState.NORMAL;
            default -> next = RadiationState.NORMAL;
        }

        setState(level, pos, next);
    }

    /**
     * 设置指定状态
     */
    public void setState(Level level, BlockPos pos, RadiationState targetState) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof RadiationSourceBlock && state.getValue(STATE) != targetState) {
            BlockState newState = state.setValue(STATE, targetState);
            level.setBlock(pos, newState, 3);

            int newStrength = getCurrentStrength(newState);
            RadiationSourceManager manager = RadiationSourceManager.get(level);
            manager.addSource(pos, newStrength);
            manager.updateAffectedArea(level, pos, true);
        }
    }

    /**
     * 调度衰变更新
     */
    private void scheduleDecay(ServerLevel level, BlockPos pos, BlockState state) {
        int decayLevel = state.getValue(DECAY_LEVEL);
        if (decayLevel >= 100) {
            // 衰变完成，移除方块
            level.removeBlock(pos, false);
            return;
        }

        // 计算下次衰变时间（半衰期 / 100）
        int nextDecayTicks = halfLifeTicks / 100;
        if (nextDecayTicks < 20) nextDecayTicks = 20;  // 最小1秒

        level.scheduleTick(pos, this, nextDecayTicks);
    }

    /**
     * Tick 更新（半衰期逻辑）
     */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
        super.tick(state, level, pos, random);

        if (!state.getValue(DECAY_ENABLED)) return;

        int currentDecay = state.getValue(DECAY_LEVEL);

        if (currentDecay < 100) {
            // 每次增加 1% 衰变进度
            int newDecay = Math.min(100, currentDecay + 1);
            BlockState newState = state.setValue(DECAY_LEVEL, newDecay);
            level.setBlock(pos, newState, 3);

            // 更新辐射强度
            int newStrength = getCurrentStrength(newState);
            RadiationSourceManager manager = RadiationSourceManager.get(level);
            manager.addSource(pos, newStrength);
            manager.updateAffectedArea(level, pos, true);

            // 调度下一次衰变
            if (newDecay < 100) {
                scheduleDecay(level, pos, newState);
            } else {
                // 衰变完成，最终清除
                level.removeBlock(pos, false);
            }
        }
    }

    /**
     * 重置衰变（调试用）
     */
    public void resetDecay(Level level, BlockPos pos, BlockState state) {
        if (!level.isClientSide && state.getValue(DECAY_ENABLED)) {
            BlockState newState = state.setValue(DECAY_LEVEL, 0);
            level.setBlock(pos, newState, 3);

            int newStrength = getCurrentStrength(newState);
            RadiationSourceManager manager = RadiationSourceManager.get(level);
            manager.addSource(pos, newStrength);
            manager.updateAffectedArea(level, pos, true);

            if (level instanceof ServerLevel serverLevel) {
                scheduleDecay(serverLevel, pos, newState);
            }
        }
    }

    /**
     * 根据衰变等级计算剩余强度百分比
     * 公式：剩余% = 100% × (0.5)^(衰变等级/100)
     */
    private double getRemainingStrengthPercent(int decayLevel) {
        return 100.0 * Math.pow(0.5, decayLevel / 100.0);
    }

    /**
     * 获取当前状态的辐射强度（考虑衰变）
     */
    public int getCurrentStrength(BlockState state) {
        // 获取基础强度
        RadiationState currentState = state.getValue(STATE);
        int baseStrength = switch (currentState) {
            case NORMAL -> normalStrength;
            case EXCITED -> excitedStrength;
            case CRITICAL -> criticalStrength;
        };

        // 应用衰变系数
        if (state.getValue(DECAY_ENABLED)) {
            int decayLevel = state.getValue(DECAY_LEVEL);
            double decayFactor = Math.pow(0.5, decayLevel / 100.0);
            baseStrength = (int)(baseStrength * decayFactor);
        }

        return Math.max(0, Math.min(100000, baseStrength));
    }

    /**
     * 获取当前衰变等级（0-100）
     */
    public int getDecayLevel(BlockState state) {
        return state.getValue(DECAY_LEVEL);
    }

    /**
     * 获取剩余强度百分比
     */
    public double getRemainingPercent(BlockState state) {
        return getRemainingStrengthPercent(state.getValue(DECAY_LEVEL));
    }

    public int getNormalStrength() { return normalStrength; }
    public int getExcitedStrength() { return excitedStrength; }
    public int getCriticalStrength() { return criticalStrength; }
    public int getHalfLifeTicks() { return halfLifeTicks; }
    public boolean isDecayEnabled() { return halfLifeTicks > 0; }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        int normalRadius = EnvironmentRadiationData.getDynamicRadius(normalStrength);
        int excitedRadius = EnvironmentRadiationData.getDynamicRadius(excitedStrength);
        int criticalRadius = EnvironmentRadiationData.getDynamicRadius(criticalStrength);

        tooltip.add(Component.literal("§7三态辐射源").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("  §a普通态§r: " + normalStrength + " 强度 / " + normalRadius + " 格范围"));
        tooltip.add(Component.literal("  §6激发态§r: " + excitedStrength + " 强度 / " + excitedRadius + " 格范围"));
        tooltip.add(Component.literal("  §c临界态§r: " + criticalStrength + " 强度 / " + criticalRadius + " 格范围"));

        if (halfLifeTicks > 0) {
            double halfLifeSec = halfLifeTicks / 20.0;
            tooltip.add(Component.literal(""));
            tooltip.add(Component.literal("§e⏳ 半衰期: " + halfLifeSec + " 秒").withStyle(ChatFormatting.YELLOW));
            tooltip.add(Component.literal("§7  强度会随时间指数衰减").withStyle(ChatFormatting.GRAY));
        }

        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("右键点击切换状态").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("手持钟右键查看衰变进度").withStyle(ChatFormatting.GRAY));
    }
}