package org.XYccWA.create_modern_technology.Items;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.XYccWA.create_modern_technology.Create_modern_technology;

public class ModernTechnologyCeativeModeTab {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Create_modern_technology.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MODERN_TECHNOLOGY_TAB = CREATIVE_MODE_TABS.register("modern_technology_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.create_modern_technology.creative_tab"))
            .icon(() -> new ItemStack(ModernTechnologyItems.ALUMINUM_INGOTS.get()))
            .displayItems((parameters, output) -> {
                output.accept(ModernTechnologyItems.ALUMINUM_INGOTS.get());
                output.accept(ModernTechnologyItems.NICKEL_INGOTS.get());
                output.accept(ModernTechnologyItems.TIN_INGOTS.get());
                output.accept(ModernTechnologyItems.COBALT_INGOTS.get());
                output.accept(ModernTechnologyItems.LEAD_INGOT.get());
                output.accept(ModernTechnologyItems.STEEL_INGOT.get());
                output.accept(ModernTechnologyItems.STAINLESS_STEEL_INGOT.get());
                output.accept(ModernTechnologyItems.ALUMINUM_ALLOY_INGOT.get());
                output.accept(ModernTechnologyItems.NICKEL_BASED_ALLOY_INGOT.get());
                output.accept(ModernTechnologyItems.COBALT_BASED_ALLOY_INGOT.get());




                output.accept(ModernTechnologyItems.RADIATION_SOURCE_BLOCK.get());
            })
            .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
