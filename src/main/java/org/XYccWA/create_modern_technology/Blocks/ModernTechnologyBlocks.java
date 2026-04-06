package org.XYccWA.create_modern_technology.Blocks;

import com.simibubi.create.foundation.data.CreateRegistrate;
import net.minecraft.world.level.block.Block;
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

    public static final RegistryObject<Block> RADIATION_SOURCE = BLOCKS.register("radiation_source",
            () -> new RadiationSourceBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GREEN)
                    .strength(2.0f)
                    .requiresCorrectToolForDrops(),
                    200
            ));

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
