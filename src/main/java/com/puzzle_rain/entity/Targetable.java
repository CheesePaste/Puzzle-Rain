package com.puzzle_rain.entity;

import net.minecraft.util.math.Vec3d;

public interface Targetable {
    Vec3d getDir();

    boolean isClose();
}
