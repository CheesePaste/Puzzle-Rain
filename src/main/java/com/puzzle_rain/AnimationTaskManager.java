package com.puzzle_rain;

import net.minecraft.server.world.ServerWorld;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class AnimationTaskManager {
    private final Map<String, CompletableFuture<Void>> activeTasks = new ConcurrentHashMap<>();

    public CompletableFuture<Void> startAnimation(ServerWorld world, BlockBounds bounds, String taskId) {
        // 创建新的动画任务
        CompletableFuture<Void> task = BlockTransitionTask.create(world, bounds)
            .execute();

        // 保存任务引用
        activeTasks.put(taskId, task);
        
        // 任务完成后清理引用
        task.whenComplete((result, throwable) -> {
            activeTasks.remove(taskId);
            if (throwable != null) {
                PuzzleRain.LOGGER.error("Animation task failed: " + throwable.getMessage(), throwable);
            }
        });

        return task;
    }

    public boolean isTaskActive(String taskId) {
        CompletableFuture<Void> task = activeTasks.get(taskId);
        return task != null && !task.isDone();
    }

    public void cancelTask(String taskId) {
        CompletableFuture<Void> task = activeTasks.get(taskId);
        if (task != null) {
            // Note: CompletableFuture doesn't have a direct cancel method
            // This is a simplified approach - in real implementation, you'd need 
            // a more sophisticated cancellation mechanism
            activeTasks.remove(taskId);
        }
    }

    public int getActiveTaskCount() {
        // Clean up completed tasks first
        activeTasks.entrySet().removeIf(entry -> entry.getValue().isDone());
        return activeTasks.size();
    }
}