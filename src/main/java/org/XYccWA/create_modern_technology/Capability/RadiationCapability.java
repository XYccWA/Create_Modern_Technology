package org.XYccWA.create_modern_technology.Capability;

import org.XYccWA.create_modern_technology.Network.RadiationSyncPacket;
import org.XYccWA.create_modern_technology.Create_modern_technology;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.network.PacketDistributor;

public class RadiationCapability implements IRadiation, INBTSerializable<CompoundTag> {
    private int radiation = 0;
    private static final int MAX_RADIATION = 500;

    // 持有该能力的玩家（用于同步）
    private ServerPlayer player;

    public RadiationCapability() {}

    public RadiationCapability(ServerPlayer player) {
        this.player = player;
    }

    @Override
    public int getRadiation() {
        return radiation;
    }

    @Override
    public void setRadiation(int value) {
        int oldValue = this.radiation;
        this.radiation = Math.min(MAX_RADIATION, Math.max(0, value));

        // 值变化时同步到客户端
        if (oldValue != this.radiation && player != null) {
            syncToClient();
        }
    }

    @Override
    public void addRadiation(int value) {
        setRadiation(this.radiation + value);
    }

    @Override
    public void subtractRadiation(int value) {
        setRadiation(this.radiation - value);
    }

    // 同步到客户端
    public void syncToClient() {
        if (player != null) {
            Create_modern_technology.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new RadiationSyncPacket(this.radiation, player.getId())
            );
        }
    }

    // 设置关联的玩家
    public void setPlayer(ServerPlayer player) {
        this.player = player;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("radiation", radiation);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        radiation = tag.getInt("radiation");
    }
}