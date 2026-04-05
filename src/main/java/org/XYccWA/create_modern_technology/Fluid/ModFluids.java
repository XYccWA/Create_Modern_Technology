package org.XYccWA.create_modern_technology.Fluid;

import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.XYccWA.create_modern_technology.Create_modern_technology;
import org.XYccWA.create_modern_technology.Blocks.ModernTechnologyBlocks;
import org.XYccWA.create_modern_technology.Fluid.ModFluidTypes;
import org.XYccWA.create_modern_technology.Items.ModernTechnologyItems;

/**
 * 之后我们书写自己的流体的类，在流体中，有流体和流体类型，这两个类都需要写。
 * 先写这里的流体的类吧
 *
 */
public class ModFluids {
    // DeferredRegister对象，这次的注册的是流体。
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(ForgeRegistries.FLUIDS, Create_modern_technology.MOD_ID);


    // 等会在modid那个类中写的。注册在总线上
    public static void register(IEventBus eventBus) {
        FLUIDS.register(eventBus);
    }
}