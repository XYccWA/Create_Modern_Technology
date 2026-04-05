package org.XYccWA.create_modern_technology.Network;

import org.XYccWA.create_modern_technology.Client.HUD.RadiationHUD;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class EnvironmentRadiationSyncPacket {
    private final Map<BlockPos, Integer> radiationMap;

    public EnvironmentRadiationSyncPacket(Map<BlockPos, Integer> radiationMap) {
        this.radiationMap = radiationMap;
    }

    // 编码
    public static void encode(EnvironmentRadiationSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.radiationMap.size());
        for (Map.Entry<BlockPos, Integer> entry : msg.radiationMap.entrySet()) {
            buf.writeBlockPos(entry.getKey());
            buf.writeInt(entry.getValue());
        }
    }

    // 解码
    public static EnvironmentRadiationSyncPacket decode(FriendlyByteBuf buf) {
        int size = buf.readInt();
        Map<BlockPos, Integer> map = new HashMap<>();
        for (int i = 0; i < size; i++) {
            BlockPos pos = buf.readBlockPos();
            int intensity = buf.readInt();
            map.put(pos, intensity);
        }
        return new EnvironmentRadiationSyncPacket(map);
    }

    // 处理
    public static void handle(EnvironmentRadiationSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // 更新客户端缓存的环境辐射数据
            RadiationHUD.updateEnvironmentRadiation(msg.radiationMap);
        });
        ctx.get().setPacketHandled(true);
    }
}