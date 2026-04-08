package org.XYccWA.create_modern_technology.Radiation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.Event;

import java.util.concurrent.*;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class RadiationThreadPool {

    // 单线程池，确保计算顺序
    private static final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Radiation-Calculator");
        t.setDaemon(true);  // 守护线程，不会阻止JVM关闭
        return t;
    });

    // 待处理任务队列（用于去重）
    private static final Map<BlockPos, PendingTask> pendingTasks = new ConcurrentHashMap<>();

    // 是否正在处理
    private static final AtomicBoolean isProcessing = new AtomicBoolean(false);

    // 任务队列（FIFO）
    private static final BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<>();

    // 任务处理线程
    private static Thread workerThread;
    private static volatile boolean running = true;

    static {
        startWorker();
    }

    private static void startWorker() {
        workerThread = new Thread(() -> {
            while (running) {
                try {
                    Runnable task = taskQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (task != null) {
                        task.run();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "Radiation-Worker");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    /**
     * 提交辐射场更新任务（去重）
     */
    public static void submitUpdate(Level level, BlockPos pos, boolean isAdding, int strength) {
        if (level == null || level.isClientSide) return;

        // 创建唯一键
        String key = pos.getX() + "," + pos.getY() + "," + pos.getZ();

        // 去重：如果已经有相同位置的任务，先移除旧的
        PendingTask existing = pendingTasks.remove(pos);

        // 创建新任务
        PendingTask task = new PendingTask(level, pos, isAdding, strength);
        pendingTasks.put(pos, task);

        // 提交到队列
        taskQueue.offer(() -> {
            try {
                PendingTask currentTask = pendingTasks.get(pos);
                if (currentTask == task) {
                    executeUpdate(currentTask.level, currentTask.pos, currentTask.isAdding, currentTask.strength);
                    pendingTasks.remove(pos, task);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * 提交批量更新任务（用于多个方块同时破坏）
     */
    public static void submitBatchUpdate(Map<BlockPos, UpdateData> updates) {
        if (updates.isEmpty()) return;

        taskQueue.offer(() -> {
            try {
                executeBatchUpdate(updates);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * 执行单个更新（在后台线程中）
     */
    private static void executeUpdate(Level level, BlockPos pos, boolean isAdding, int strength) {

        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                new RadiationUpdateEvent(level, pos, isAdding, strength)
        );
    }

    /**
     * 执行批量更新
     */
    private static void executeBatchUpdate(Map<BlockPos, UpdateData> updates) {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                new BatchRadiationUpdateEvent(updates)
        );
    }

    /**
     * 关闭线程池
     */
    public static void shutdown() {
        running = false;
        if (workerThread != null) {
            workerThread.interrupt();
        }
        executor.shutdown();
    }

    // 内部类：待处理任务
    private static class PendingTask {
        final Level level;
        final BlockPos pos;
        final boolean isAdding;
        final int strength;

        PendingTask(Level level, BlockPos pos, boolean isAdding, int strength) {
            this.level = level;
            this.pos = pos;
            this.isAdding = isAdding;
            this.strength = strength;
        }
    }

    // 更新数据
    public static class UpdateData {
        public final BlockPos pos;
        public final int strength;
        public final boolean isAdding;

        public UpdateData(BlockPos pos, int strength, boolean isAdding) {
            this.pos = pos;
            this.strength = strength;
            this.isAdding = isAdding;
        }
    }

    // 事件：单个辐射场更新
    public static class RadiationUpdateEvent extends Event {
        public final Level level;
        public final BlockPos pos;
        public final boolean isAdding;
        public final int strength;

        public RadiationUpdateEvent(Level level, BlockPos pos, boolean isAdding, int strength) {
            this.level = level;
            this.pos = pos;
            this.isAdding = isAdding;
            this.strength = strength;
        }
    }

    // 事件：批量辐射场更新
    public static class BatchRadiationUpdateEvent extends Event {
        public final Map<BlockPos, UpdateData> updates;

        public BatchRadiationUpdateEvent(Map<BlockPos, UpdateData> updates) {
            this.updates = new HashMap<>(updates);
        }
    }
}