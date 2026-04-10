package org.XYccWA.create_modern_technology.Items;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.XYccWA.create_modern_technology.Blocks.ModernTechnologyBlocks;
import org.XYccWA.create_modern_technology.Items.Tools.GeigerCounterItem;

public class ModernTechnologyItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "create_modern_technology");

    //纯金属锭
    public static final RegistryObject<Item> ALUMINUM_INGOTS = ITEMS.register("aluminum_ingot", () -> new Item(new Item.Properties().stacksTo(1000)));
    public static final RegistryObject<Item> NICKEL_INGOTS = ITEMS.register("nickel_ingot", () -> new Item(new Item.Properties().stacksTo(1000)));
    public static final RegistryObject<Item> TIN_INGOTS = ITEMS.register("tin_ingot", () -> new Item(new Item.Properties().stacksTo(1000)));
    public static final RegistryObject<Item> COBALT_INGOTS = ITEMS.register("cobalt_ingot", () -> new Item(new Item.Properties().stacksTo(1000)));
    public static final RegistryObject<Item> LEAD_INGOT = ITEMS.register("lead_ingot", () -> new Item(new Item.Properties().stacksTo(1000)));

    //合金锭
    public static final RegistryObject<Item> STEEL_INGOT = ITEMS.register("steel_ingot", () -> new Item(new Item.Properties().stacksTo(1000)));
    public static final RegistryObject<Item> STAINLESS_STEEL_INGOT = ITEMS.register("stainless_steel_ingot", () -> new Item(new Item.Properties().stacksTo(1000)));
    public static final RegistryObject<Item> ALUMINUM_ALLOY_INGOT = ITEMS.register("aluminum_alloy_ingot", () -> new Item(new Item.Properties().stacksTo(1000)));
    public static final RegistryObject<Item> NICKEL_BASED_ALLOY_INGOT = ITEMS.register("nickel_based_alloy_ingot", () -> new Item(new Item.Properties().stacksTo(1000)));
    public static final RegistryObject<Item> COBALT_BASED_ALLOY_INGOT = ITEMS.register("cobalt_based_alloy_ingot", () -> new Item(new Item.Properties().stacksTo(1000)));

    //矿石
    public static final RegistryObject<Item> CHROMITE_ORE = ITEMS.register("chromite_ore", () -> new Item(new Item.Properties().stacksTo(200)));
    public static final RegistryObject<Item> CRYOLITE_ORE = ITEMS.register("cryolite_ore", () -> new Item(new Item.Properties().stacksTo(200)));
    public static final RegistryObject<Item> BAUXITE_ORE = ITEMS.register("bauxite_ore", () -> new Item(new Item.Properties().stacksTo(200)));


    //工具
    public static final RegistryObject<Item> GEIGER_COUNTER = ITEMS.register("geiger_counter",
            () -> new GeigerCounterItem(new Item.Properties().stacksTo(1)));










    //DEBUG
    public static final RegistryObject<Item> RADIATION_SOURCE_BLOCK = ITEMS.register("radiation_source_block", () -> new BlockItem(ModernTechnologyBlocks.RADIATION_SOURCE.get(), new Item.Properties()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
