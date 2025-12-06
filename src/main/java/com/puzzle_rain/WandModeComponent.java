package com.puzzle_rain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.dynamic.Codecs;

// 魔杖模式组件数据类
public class WandModeComponent {
    private final int modeIndex;

    public static final Codec<WandModeComponent> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codecs.rangedInt(0, 2).fieldOf("mode").forGetter(WandModeComponent::getModeIndex)
            ).apply(instance, WandModeComponent::new)
    );

    public static final PacketCodec<ByteBuf, WandModeComponent> PACKET_CODEC =
            PacketCodecs.INTEGER.xmap(WandModeComponent::new, WandModeComponent::getModeIndex);

    public WandModeComponent(int modeIndex) {
        this.modeIndex = modeIndex;
    }

    public int getModeIndex() {
        return modeIndex;
    }
}
