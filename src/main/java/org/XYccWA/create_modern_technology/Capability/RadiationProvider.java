package org.XYccWA.create_modern_technology.Capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RadiationProvider implements ICapabilityProvider, ICapabilitySerializable<CompoundTag> {
    public static final Capability<IRadiation> RADIATION_CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {});

    private RadiationCapability radiation;
    private final LazyOptional<IRadiation> holder;

    public RadiationProvider() {
        this.radiation = new RadiationCapability();
        this.holder = LazyOptional.of(() -> radiation);
    }

    public RadiationProvider(ServerPlayer player) {
        this.radiation = new RadiationCapability(player);
        this.holder = LazyOptional.of(() -> radiation);
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return cap == RADIATION_CAPABILITY ? holder.cast() : LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        return radiation.serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        radiation.deserializeNBT(tag);
    }
}