package org.XYccWA.create_modern_technology.Network;

import org.XYccWA.create_modern_technology.Capability.RadiationProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RadiationSyncPacket {
    private final int radiationValue;
    private final int playerId;

    public RadiationSyncPacket(int radiationValue, int playerId) {
        this.radiationValue = radiationValue;
        this.playerId = playerId;
    }

    // 编码
    public static void encode(RadiationSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.radiationValue);
        buf.writeInt(msg.playerId);
    }

    // 解码
    public static RadiationSyncPacket decode(FriendlyByteBuf buf) {
        return new RadiationSyncPacket(buf.readInt(), buf.readInt());
    }

    // 处理 - 修正版
    public static void handle(RadiationSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // 客户端直接获取当前玩家
            Player player = Minecraft.getInstance().player;

            // 验证玩家ID是否匹配（可选）
            if (player != null && player.getId() == msg.playerId) {
                player.getCapability(RadiationProvider.RADIATION_CAPABILITY).ifPresent(rad -> {
                    rad.setRadiation(msg.radiationValue);
                });
            }
        });
        ctx.get().setPacketHandled(true);
    }
}