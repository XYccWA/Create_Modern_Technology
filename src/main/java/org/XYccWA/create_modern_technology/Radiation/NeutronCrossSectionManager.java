package org.XYccWA.create_modern_technology.Radiation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.XYccWA.create_modern_technology.Blocks.RadiationSourceBlock;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class NeutronCrossSectionManager {

    // 存储方块的中子截面值（基于方块本身）
    private static final Map<Block, Double> blockCrossSections = new HashMap<>();

    // 存储方块状态/BlockEntity的中子截面值计算函数
    private static final Map<Block, Function<BlockState, Double>> dynamicCrossSections = new HashMap<>();

    // 默认配置
    static {
        // 中子减速剂（中等截面值）
        registerBlockCrossSection("minecraft:water", 0.2);
        registerDynamicCrossSection("create_modern_technology:radiation_source", state -> {
            // 获取辐射源的状态
            if (state.hasProperty(RadiationSourceBlock.STATE)) {
                RadiationSourceBlock.RadiationState status = state.getValue(RadiationSourceBlock.STATE);
                if (status == RadiationSourceBlock.RadiationState.NORMAL) {
                    return 0.1;
                }
                if (status == RadiationSourceBlock.RadiationState.EXCITED) {
                    return 0.15;
                }
                if (status == RadiationSourceBlock.RadiationState.CRITICAL) {
                    return 1.0;
                }
            }
            return 0.0; // 其他状态默认 0
        });
    }

    public static void registerBlockCrossSection(String blockId, double crossSection) {
        Block block = ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation(blockId));
        if (block != null) {
            blockCrossSections.put(block, crossSection);
        }
    }

    public static void registerDynamicCrossSection(String blockId, Function<BlockState, Double> calculator) {
        Block block = ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation(blockId));
        if (block != null) {
            dynamicCrossSections.put(block, calculator);
        }
    }

    /**
     * 获取方块的中子截面值
     */
    public static double getCrossSection(BlockState state) {
        Block block = state.getBlock();

        // 优先使用动态计算
        if (dynamicCrossSections.containsKey(block)) {
            return dynamicCrossSections.get(block).apply(state);
        }

        // 使用静态配置
        return blockCrossSections.getOrDefault(block, 0.0);
    }

    /**
     * 获取某个位置的方块中子截面值
     */
    public static double getCrossSectionAt(Level level, BlockPos pos) {
        return getCrossSection(level.getBlockState(pos));
    }

    /**
     * 计算辐射源周围6个方向的总中子截面值
     */
    public static double calculateTotalCrossSection(Level level, BlockPos centerPos) {
        double total = 0.0;

        // 上下前后左右6个方向
        BlockPos[] directions = {
                centerPos.above(),  // 上
                centerPos.below(),  // 下
                centerPos.north(),  // 前
                centerPos.south(),  // 后
                centerPos.west(),   // 左
                centerPos.east()    // 右
        };

        for (BlockPos pos : directions) {
            total += getCrossSectionAt(level, pos);
        }

        return total;
    }

    /**
     * 根据中子截面值判断状态
     * @param crossSection 总中子截面值
     * @return 0=普通态, 1=激发态, 2=临界态
     */
    public static int getStateByCrossSection(double crossSection) {
        if (crossSection >= 1.0) return 2;  // 临界态
        if (crossSection >= 0.8) return 1;  // 激发态
        return 0;                            // 普通态
    }
}