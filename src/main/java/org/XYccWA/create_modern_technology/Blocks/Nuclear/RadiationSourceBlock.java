package org.XYccWA.create_modern_technology.Blocks.Nuclear;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.util.StringRepresentable;
import org.XYccWA.create_modern_technology.BlockEntities.Nuclear.RadiationSourceBlockEntity;
import org.XYccWA.create_modern_technology.Radiation.RadiationUpdateThreadManager;
import org.XYccWA.create_modern_technology.World.RadiationSourceManager;
import org.jetbrains.annotations.Nullable;

public class RadiationSourceBlock extends Block implements EntityBlock {

    public enum RadiationState implements StringRepresentable {
        NORMAL("normal"),
        EXCITED("excited"),
        CRITICAL("critical");

        private final String name;

        RadiationState(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() { return name; }
    }

    public static final EnumProperty<RadiationState> STATE = EnumProperty.create("state", RadiationState.class);

    // NBT 键名
    public static final String NBT_FUEL = "radiation_fuel";
    public static final String NBT_STATE = "radiation_state";
    public static final String NBT_CRITICAL_TIMER = "radiation_critical_timer";
    public static final String NBT_OVERHEAT_TIMER = "radiation_overheat_timer";

    // 三态辐射强度配置
    private final int normalStrength;
    private final int excitedStrength;
    private final int criticalStrength;

    // 燃料配置
    private final int maxFuel;
    private final int normalConsumption;
    private final int excitedConsumption;

    // 燃料阈值配置
    private final double excitedThreshold;
    private final double criticalThreshold;
    private final double wasteThreshold;

    // 核废料配置
    private final Block nuclearWasteBlock;

    // 临界态超时时间
    private final int criticalOverheatTime;

    // 爆炸产物
    private final Block explosionResultBlock;

    /**
     * 完整构造函数
     */
    public RadiationSourceBlock(Properties properties,
                                int normalStrength, int excitedStrength, int criticalStrength,
                                int maxFuel, int normalConsumption, int excitedConsumption,
                                Block nuclearWasteBlock, int criticalOverheatTime,
                                Block explosionResultBlock,
                                double excitedThreshold, double criticalThreshold, double wasteThreshold) {
        super(properties);
        this.normalStrength = Math.min(100000, Math.max(0, normalStrength));
        this.excitedStrength = Math.min(100000, Math.max(0, excitedStrength));
        this.criticalStrength = Math.min(100000, Math.max(0, criticalStrength));
        this.maxFuel = Math.max(0, maxFuel);
        this.normalConsumption = Math.max(0, normalConsumption);
        this.excitedConsumption = Math.max(0, excitedConsumption);
        this.nuclearWasteBlock = nuclearWasteBlock;
        this.criticalOverheatTime = Math.max(20, criticalOverheatTime);
        this.explosionResultBlock = explosionResultBlock;
        this.excitedThreshold = Math.min(1.0, Math.max(0.0, excitedThreshold));
        this.criticalThreshold = Math.min(1.0, Math.max(0.0, criticalThreshold));
        this.wasteThreshold = Math.min(1.0, Math.max(0.0, wasteThreshold));
        this.registerDefaultState(this.stateDefinition.any().setValue(STATE, RadiationState.NORMAL));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STATE);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RadiationSourceBlockEntity(pos, state, maxFuel, normalConsumption, excitedConsumption,
                criticalOverheatTime, excitedThreshold, criticalThreshold, wasteThreshold);
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

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be == null) {
                be = newBlockEntity(pos, state);
                level.setBlockEntity(be);
            }

            int strength = getCurrentStrength(state);
            RadiationSourceManager.get(level).addSource(pos, strength);
            RadiationUpdateThreadManager.queueRadiationUpdate(pos, true, strength);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            RadiationSourceManager.get(level).removeSource(pos);
            RadiationUpdateThreadManager.queueRadiationUpdate(pos, false, 0);

            if (level instanceof ServerLevel serverLevel) {
                serverLevel.players().forEach(player -> {
                    if (player.blockPosition().distSqr(pos) <= 128 * 128) {
                        RadiationUpdateThreadManager.syncPlayerPosition((ServerPlayer) player);
                    }
                });
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    /**
     * 挖掘时保存 NBT 到掉落物
     */
    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool) {
        if (!level.isClientSide && blockEntity instanceof RadiationSourceBlockEntity tile) {
            RadiationState currentState = state.getValue(STATE);

            // 激发态或临界态挖掘时触发爆炸
            if (currentState == RadiationState.EXCITED || currentState == RadiationState.CRITICAL) {
                tile.explodeOnMine();
                return;
            }

            // 普通态：掉落带有 NBT 的物品
            ItemStack dropStack = new ItemStack(this.asItem());
            CompoundTag tag = new CompoundTag();
            tag.putInt(NBT_FUEL, tile.getFuel());
            tag.putString(NBT_STATE, currentState.getSerializedName());
            tag.putInt(NBT_CRITICAL_TIMER, tile.getCriticalTimer());
            tag.putInt(NBT_OVERHEAT_TIMER, tile.getCriticalOverheatTimer());
            dropStack.setTag(tag);

            // 掉落物品
            popResource(level, pos, dropStack);

            // 移除方块
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
            if (be instanceof RadiationSourceBlockEntity tile && stack.hasTag()) {
                CompoundTag tag = stack.getTag();
                if (tag.contains(NBT_FUEL)) {
                    tile.setFuel(tag.getInt(NBT_FUEL));
                }
                if (tag.contains(NBT_CRITICAL_TIMER)) {
                    tile.setCriticalTimer(tag.getInt(NBT_CRITICAL_TIMER));
                }
                if (tag.contains(NBT_OVERHEAT_TIMER)) {
                    tile.setCriticalOverheatTimer(tag.getInt(NBT_OVERHEAT_TIMER));
                }
                if (tag.contains(NBT_STATE)) {
                    String stateName = tag.getString(NBT_STATE);
                    RadiationState targetState = switch (stateName) {
                        case "excited" -> RadiationState.EXCITED;
                        case "critical" -> RadiationState.CRITICAL;
                        default -> RadiationState.NORMAL;
                    };
                    if (targetState != RadiationState.NORMAL) {
                        setState(level, pos, targetState);
                    }
                }
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
            RadiationState next = null;

            int fuel = tile.getFuel();
            double fuelPercent = (double) fuel / maxFuel;

            switch (current) {
                case NORMAL -> {
                    if (fuelPercent >= excitedThreshold) {
                        next = RadiationState.EXCITED;
                    }
                }
                case EXCITED -> {
                    if (fuelPercent >= criticalThreshold) {
                        next = RadiationState.CRITICAL;
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
            RadiationUpdateThreadManager.queueRadiationUpdate(pos, true, newStrength);
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

    // Getters
    public int getNormalStrength() { return normalStrength; }
    public int getExcitedStrength() { return excitedStrength; }
    public int getCriticalStrength() { return criticalStrength; }
    public int getMaxFuel() { return maxFuel; }
    public Block getNuclearWasteBlock() { return nuclearWasteBlock; }
    public int getCriticalOverheatTime() { return criticalOverheatTime; }
    public Block getExplosionResultBlock() { return explosionResultBlock; }
    public double getExcitedThreshold() { return excitedThreshold; }
    public double getCriticalThreshold() { return criticalThreshold; }
    public double getWasteThreshold() { return wasteThreshold; }
}