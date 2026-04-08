package org.XYccWA.create_modern_technology.Handler;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.XYccWA.create_modern_technology.World.RadiationSourceManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BreakHandler {

    // 批量破坏缓冲区（收集短时间内破坏的方块）
    private static final Map<Level, Map<BlockPos, Boolean>> breakBuffer = new ConcurrentHashMap<>();
    private static final Map<Level, Long> lastFlushTime = new ConcurrentHashMap<>();
    private static final int BATCH_WINDOW_MS = 100;  // 100ms 内的破坏合并处理

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        Level level = (Level) event.getLevel();
        BlockPos pos = event.getPos();

        // 收集到缓冲区
        Map<BlockPos, Boolean> buffer = breakBuffer.computeIfAbsent(level, k -> new ConcurrentHashMap<>());
        buffer.put(pos, false);  // false = 移除

        long now = System.currentTimeMillis();
        Long lastFlush = lastFlushTime.get(level);

        if (lastFlush == null || now - lastFlush >= BATCH_WINDOW_MS) {
            flushBuffer(level);
        }
    }

    private void flushBuffer(Level level) {
        Map<BlockPos, Boolean> buffer = breakBuffer.remove(level);
        if (buffer == null || buffer.isEmpty()) return;

        lastFlushTime.put(level, System.currentTimeMillis());

        // 批量更新
        RadiationSourceManager manager = RadiationSourceManager.get(level);
        manager.batchUpdateAsync(buffer);
    }
}