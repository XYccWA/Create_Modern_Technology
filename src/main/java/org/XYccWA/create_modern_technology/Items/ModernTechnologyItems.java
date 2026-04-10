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










    // 方块物品 - 放射性铀裂变废料块
    public static final RegistryObject<Item> RADIOACTIVE_URANIUM_FISSION_WASTE_BLOCK = ITEMS.register("radioactive_uranium_fission_waste_block", 
        () -> new BlockItem(ModernTechnologyBlocks.RADIOACTIVE_URANIUM_FISSION_WASTE_BLOCK.get(), new Item.Properties()));
    
    // 方块物品 - 铀裂变废料块
    public static final RegistryObject<Item> URANIUM_FISSION_SCRAP_BLOCK = ITEMS.register("uranium_fission_scrap_block", 
        () -> new BlockItem(ModernTechnologyBlocks.URANIUM_FISSION_SCRAP_BLOCK.get(), new Item.Properties()));
    
    // 方块物品 - 熔融铀废料
    public static final RegistryObject<Item> MOLTEN_URANIUM_FUEL = ITEMS.register("molten_uranium_waste", 
        () -> new BlockItem(ModernTechnologyBlocks.MOLTEN_URANIUM_FUEL.get(), new Item.Properties()));
    
    // 方块物品 - 铀燃料块
    public static final RegistryObject<Item> URANIUM_FUEL_BLOCK = ITEMS.register("uranium_fuel_block", 
        () -> new BlockItem(ModernTechnologyBlocks.URANIUM_FUEL_BLOCK.get(), new Item.Properties()));
    
    // 方块物品 - 熔融核废料
    public static final RegistryObject<Item> MOLTEN_NUCLEAR_WASTE = ITEMS.register("molten_nuclear_waste", 
        () -> new BlockItem(ModernTechnologyBlocks.MOLTEN_NUCLEAR_WASTE.get(), new Item.Properties()));

    //DEBUG
    public static final RegistryObject<Item> RADIATION_SOURCE_BLOCK = ITEMS.register("radiation_source_block", () -> new BlockItem(ModernTechnologyBlocks.RADIATION_SOURCE.get(), new Item.Properties()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
