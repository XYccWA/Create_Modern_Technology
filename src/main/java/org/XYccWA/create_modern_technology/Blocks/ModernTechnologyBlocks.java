package org.XYccWA.create_modern_technology.Blocks;

import com.simibubi.create.foundation.data.CreateRegistrate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.XYccWA.create_modern_technology.Create_modern_technology;

public class ModernTechnologyBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Create_modern_technology.MOD_ID);
    public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(Create_modern_technology.MOD_ID);




    public static final RegistryObject<Block> RADIOACTIVE_URANIUM_FISSION_WASTE_BLOCK = BLOCKS.register("radioactive_uranium_fission_waste_block",() -> new Block(BlockBehaviour.Properties.of()));


    public static final RegistryObject<Block> URANIUM_FISSION_SCRAP_BLOCK = BLOCKS.register("uranium_fission_scrap_block",
            () -> new NuclearWasteBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_GREEN)
                            .strength(1.2f)
                            .lightLevel(state -> 4),
                    20,           // 初始辐射强度
                    0.03,           // 每天衰变3%（半衰期约23天）
                    RADIOACTIVE_URANIUM_FISSION_WASTE_BLOCK.get()
            ));


    public static final RegistryObject<Block> URANIUM_FUEL_BLOCK = BLOCKS.register("uranium_fuel_block",
            () -> new RadiationSourceBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(2.0f),
                    10, 100, 500,
                    50000,
                    2,
                    10,
                    ModernTechnologyBlocks.URANIUM_FISSION_SCRAP_BLOCK.get(),
                    200
            ));


















    //默认

    public static final RegistryObject<Block> RADIOACTIVE_NUCLEAR_WASTE = BLOCKS.register("radioactive_nuclear_waste",() -> new Block(BlockBehaviour.Properties.of()));


    public static final RegistryObject<Block> NUCLEAR_WASTE = BLOCKS.register("nuclear_waste",
            () -> new NuclearWasteBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_GREEN)
                            .strength(1.2f)
                            .lightLevel(state -> 4),
                    20,           // 初始辐射强度
                    0.03,           // 每天衰变3%（半衰期约23天）
                    RADIOACTIVE_URANIUM_FISSION_WASTE_BLOCK.get()
            ));


    public static final RegistryObject<Block> RADIATION_SOURCE = BLOCKS.register("radiation_source",
            () -> new RadiationSourceBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(2.0f),
                    10, 100, 500,
                    50000,
                    2,
                    10,
                    ModernTechnologyBlocks.URANIUM_FISSION_SCRAP_BLOCK.get(),
                    200
            ));

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
