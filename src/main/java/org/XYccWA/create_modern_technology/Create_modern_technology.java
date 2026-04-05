package org.XYccWA.create_modern_technology;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.XYccWA.create_modern_technology.Blocks.ModernTechnologyBlocks;
import org.XYccWA.create_modern_technology.Client.HUD.RadiationHUD;
import org.XYccWA.create_modern_technology.Fluid.ModFluidTypes;
import org.XYccWA.create_modern_technology.Fluid.ModFluids;
import org.XYccWA.create_modern_technology.Handler.CapabilityHandler;
import org.XYccWA.create_modern_technology.Handler.RadiationAccumulationHandler;
import org.XYccWA.create_modern_technology.Items.ModernTechnologyCeativeModeTab;
import org.XYccWA.create_modern_technology.Items.ModernTechnologyItems;
import org.XYccWA.create_modern_technology.Network.EnvironmentRadiationSyncPacket;
import org.XYccWA.create_modern_technology.Network.RadiationSyncPacket;
import org.slf4j.Logger;


@Mod(Create_modern_technology.MOD_ID)
public class Create_modern_technology {
    public static final String MOD_ID = "create_modern_technology";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String PROTOCOL_VERSION = "1";
    public static SimpleChannel CHANNEL;

    public Create_modern_technology() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        IEventBus forgeEventBus = MinecraftForge.EVENT_BUS;


        ModernTechnologyBlocks.register(modEventBus);
        ModernTechnologyItems.register(modEventBus);
        ModFluids.register(modEventBus);
        ModFluidTypes.register(modEventBus);
        ModernTechnologyCeativeModeTab.register(modEventBus);

        // 注册网络
        modEventBus.addListener(this::setup);
        // 注册能力事件
        forgeEventBus.register(new CapabilityHandler());
        // 注册辐射累积逻辑
        forgeEventBus.register(new RadiationAccumulationHandler());
        // 注册客户端事件
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            forgeEventBus.register(RadiationHUD.class);
        });
    }

    private void setup(FMLCommonSetupEvent event) {
        CHANNEL = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(MOD_ID, "radiation_sync"),
                () -> PROTOCOL_VERSION,
                PROTOCOL_VERSION::equals,
                PROTOCOL_VERSION::equals
        );
        CHANNEL.registerMessage(0, RadiationSyncPacket.class,
                RadiationSyncPacket::encode,
                RadiationSyncPacket::decode,
                RadiationSyncPacket::handle
        );
        // 注册环境辐射同步包
        CHANNEL.registerMessage(1, EnvironmentRadiationSyncPacket.class,
                EnvironmentRadiationSyncPacket::encode,
                EnvironmentRadiationSyncPacket::decode,
                EnvironmentRadiationSyncPacket::handle
        );
    }

}
