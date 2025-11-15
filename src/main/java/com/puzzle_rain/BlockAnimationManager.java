package com.puzzle_rain;

import net.minecraft.server.world.ServerWorld;
import java.util.*;

public class BlockAnimationManager {
    private final List<BlockAnimation> activeAnimations = new ArrayList<>();
    private final SelectionManager selectionManager = new SelectionManager();

    public void startAnimation(ServerWorld world, BlockBounds bounds) {
        BlockAnimation animation = new BlockAnimation(world, bounds);
        activeAnimations.add(animation);
        animation.start();
    }

    public void tick() {
        Iterator<BlockAnimation> iterator = activeAnimations.iterator();
        while (iterator.hasNext()) {
            BlockAnimation animation = iterator.next();
            animation.tick();
            if (animation.isFinished()) {
                iterator.remove();
            }
        }
    }

    public SelectionManager getSelectionManager() {
        return selectionManager;
    }
}