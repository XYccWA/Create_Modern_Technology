package org.XYccWA.create_modern_technology.Damage;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.common.Mod;
import org.XYccWA.create_modern_technology.Create_modern_technology;

public class RadiationDamage {

    // 定义辐射伤害类型
    public static final ResourceKey<DamageType> RADIATION = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            new ResourceLocation(Create_modern_technology.MOD_ID, "radiation")
    );

    /**
     * 创建辐射伤害源
     * @return 辐射伤害源
     */
    public static DamageSource cause(LivingEntity source) {
        // 如果有伤害来源实体（如辐射源方块导致的伤害可以传 null）
        return new DamageSource(
                source.level().registryAccess()
                        .registryOrThrow(Registries.DAMAGE_TYPE)
                        .getHolderOrThrow(RADIATION),
                source  // 伤害来源实体
        );
    }

    /**
     * 创建辐射伤害源（无来源实体）
     * @param level 世界（用于获取伤害类型注册）
     * @return 辐射伤害源
     */
    public static DamageSource cause(net.minecraft.world.level.Level level) {
        return new DamageSource(
                level.registryAccess()
                        .registryOrThrow(Registries.DAMAGE_TYPE)
                        .getHolderOrThrow(RADIATION)
        );
    }
}