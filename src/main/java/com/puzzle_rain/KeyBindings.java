package com.puzzle_rain;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class KeyBindings {
    public static KeyBinding openConfigKey;
    public static KeyBinding addEmitterPointKey;

    public static void register() {
        openConfigKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.puzzle-rain.open_config", // 翻译键
                InputUtil.Type.KEYSYM, // 按键类型
                GLFW.GLFW_KEY_Y,
                KeyBinding.GAMEPLAY_CATEGORY
        ));

        addEmitterPointKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.puzzle-rain.add_emitter_point", // 翻译键
                InputUtil.Type.KEYSYM, // 按键类型
                GLFW.GLFW_KEY_I,
                KeyBinding.GAMEPLAY_CATEGORY
        ));
    }
}
