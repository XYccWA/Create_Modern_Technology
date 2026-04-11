package org.XYccWA.create_modern_technology.BlockEntities;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.XYccWA.create_modern_technology.Blocks.ModernTechnologyBlocks;
import org.XYccWA.create_modern_technology.Create_modern_technology;

public class ModernTechnologyBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Create_modern_technology.MOD_ID);

    public static final RegistryObject<BlockEntityType<RadiationSourceBlockEntity>> RADIATION_SOURCE_BE =
            BLOCK_ENTITIES.register("radiation_source_be",
                    () -> BlockEntityType.Builder.of(
                            (pos, state) -> new RadiationSourceBlockEntity(pos, state,100000, 10, 200,500,0.5,0.1,0.05),
                            ModernTechnologyBlocks.RADIATION_SOURCE.get(),
                            ModernTechnologyBlocks.URANIUM_FUEL_BLOCK.get()
                    ).build(null));

    public static final RegistryObject<BlockEntityType<NuclearWasteBlockEntity>> NUCLEAR_WASTE_BE =
            BLOCK_ENTITIES.register("nuclear_waste_be",
                    () -> BlockEntityType.Builder.of(
                            (pos,state) -> new NuclearWasteBlockEntity(pos, state, 10000,1, ModernTechnologyBlocks.RADIOACTIVE_NUCLEAR_WASTE.get()),
                            ModernTechnologyBlocks.NUCLEAR_WASTE.get(),
                            ModernTechnologyBlocks.MOLTEN_NUCLEAR_WASTE.get(),
                            ModernTechnologyBlocks.URANIUM_FISSION_SCRAP_BLOCK.get(),
                            ModernTechnologyBlocks.MOLTEN_URANIUM_FUEL.get()
                    ).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}