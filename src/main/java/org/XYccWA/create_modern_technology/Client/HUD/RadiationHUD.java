package org.XYccWA.create_modern_technology.Client.HUD;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = "radiation", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RadiationHUD {

    private static final Map<BlockPos, Integer> clientRadiationCache = new ConcurrentHashMap<>();
    private static int currentEnvRadiation = 0;

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null) return;

        // 检查是否手持盖革计数器
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();

        boolean hasGeigerCounter = isGeigerCounter(mainHand) || isGeigerCounter(offHand);

        if (!hasGeigerCounter) return;

        GuiGraphics gui = event.getGuiGraphics();
        int screenWidth = gui.guiWidth();
        int screenHeight = gui.guiHeight();

        // 更新环境辐射
        updateCurrentEnvironmentRadiation(player);

        // 只显示环境辐射强度，不显示累计值
        renderEnvironmentRadiationOnly(gui, screenWidth, screenHeight, currentEnvRadiation, mc);
    }

    private static boolean isGeigerCounter(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getItem() instanceof org.XYccWA.create_modern_technology.Items.Tools.GeigerCounterItem;
    }

    private static void updateCurrentEnvironmentRadiation(LocalPlayer player) {
        BlockPos playerPos = player.blockPosition();
        int nearestRadiation = 0;
        int searchRadius = 1;

        for (int dx = -searchRadius; dx <= searchRadius; dx++) {
            for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                BlockPos checkPos = new BlockPos(playerPos.getX() + dx, playerPos.getY(), playerPos.getZ() + dz);
                Integer rad = clientRadiationCache.get(checkPos);
                if (rad != null && rad > nearestRadiation) {
                    nearestRadiation = rad;
                }
            }
        }

        currentEnvRadiation = nearestRadiation;
    }

    public static void updateEnvironmentRadiation(Map<BlockPos, Integer> radiationMap) {
        clientRadiationCache.clear();
        clientRadiationCache.putAll(radiationMap);
    }

    /**
     * 只显示环境辐射强度
     */
    private static void renderEnvironmentRadiationOnly(GuiGraphics gui, int width, int height, int envRadiation, Minecraft mc) {
        int x = width - 100;
        int y = height - 40;

        // 背景框（更小）
        gui.fill(x - 2, y - 2, x + 92, y + 22, 0x88000000);

        // 盖革计数器图标（可选，使用文字代替）
        gui.drawString(mc.font, "§2☢", x, y + 2, 0x00FF00);

        // 环境辐射值
        String envText;
        int envColor;

        if (envRadiation > 0) {
            envText = "辐射: " + envRadiation;
            if (envRadiation >= 80) {
                envColor = 0xFF0000;
                // 危险时添加警告音效（可选，每5秒一次）
                if (mc.player != null && mc.player.tickCount % 100 == 0) {
                    mc.player.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
                }
            } else if (envRadiation >= 50) {
                envColor = 0xFF8800;
            } else if (envRadiation >= 20) {
                envColor = 0xFFFF00;
            } else {
                envColor = 0x00FF00;
            }
        } else {
            envText = "安全";
            envColor = 0x00AA00;
        }

        gui.drawString(mc.font, envText, x + 12, y + 2, envColor);

        // 危险提示
        if (envRadiation >= 60) {
            gui.drawString(mc.font, "§c⚠", x + 85, y + 2, 0xFF0000);
        }
    }
}