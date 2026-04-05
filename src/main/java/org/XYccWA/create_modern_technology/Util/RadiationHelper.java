package org.XYccWA.create_modern_technology.Util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

public class RadiationHelper {

    public static int countLeadBlocksAroundPlayer(Player player) {
        Level level = player.level();
        BlockPos playerPos = player.blockPosition();
        int leadCount = 0;
        int radius = 8;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius / 2; dy <= radius / 2; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos checkPos = playerPos.offset(dx, dy, dz);
                    if (level.getBlockState(checkPos).is(Blocks.IRON_BLOCK)) {
                        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        leadCount += (int) (1.0 / (distance + 1) * 10);
                    }
                }
            }
        }
        return leadCount;
    }
}