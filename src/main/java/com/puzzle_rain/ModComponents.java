package com.puzzle_rain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.component.ComponentType;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.dynamic.Codecs;

public class ModComponents {
    // 定义魔杖模式组件
    public static final ComponentType<WandModeComponent> WAND_MODE =
            ComponentType.<WandModeComponent>builder()
                    .codec(WandModeComponent.CODEC)
                    .packetCodec(WandModeComponent.PACKET_CODEC)
                    .build();

    public static void register() {
        Registry.register(Registries.DATA_COMPONENT_TYPE, "puzzle_rain:wand_mode", WAND_MODE);
    }

}

