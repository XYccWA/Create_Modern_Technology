package org.XYccWA.create_modern_technology.Client.HUD;

import org.XYccWA.create_modern_technology.Capability.RadiationProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = "radiation", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RadiationHUD {

    // 客户端缓存的环境辐射数据（key: 区块坐标或方块位置）
    private static final Map<BlockPos, Integer> clientRadiationCache = new ConcurrentHashMap<>();

    // 最近一次获取的当前玩家位置的环境辐射值
    private static int currentEnvRadiation = 0;

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null) return;

        GuiGraphics gui = event.getGuiGraphics();
        int screenWidth = gui.guiWidth();
        int screenHeight = gui.guiHeight();

        // 更新当前位置的环境辐射强度
        updateCurrentEnvironmentRadiation(player);

        // 获取玩家辐射值
        player.getCapability(RadiationProvider.RADIATION_CAPABILITY).ifPresent(rad -> {
            int radiationValue = rad.getRadiation();

            // 绘制 HUD
            renderRadiationInfo(gui, screenWidth, screenHeight, radiationValue, currentEnvRadiation, mc);
        });
    }

    /**
     * 更新当前玩家位置的环境辐射强度
     */
    private static void updateCurrentEnvironmentRadiation(LocalPlayer player) {
        BlockPos playerPos = player.blockPosition();

        // 查找玩家周围最近的有效辐射值
        // 由于环境辐射是按每个方块位置存储的，我们需要找最近的记录
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

    /**
     * 从服务端接收环境辐射数据并更新缓存
     */
    public static void updateEnvironmentRadiation(Map<BlockPos, Integer> radiationMap) {
        clientRadiationCache.clear();
        clientRadiationCache.putAll(radiationMap);
    }

    private static void renderRadiationInfo(GuiGraphics gui, int width, int height, int radiation, int envRadiation, Minecraft mc) {
        int x = width - 130;  // 右侧
        int y = height - 80;   // 底部

        // 背景框（扩大高度以容纳环境辐射）
        gui.fill(x - 2, y - 2, x + 122, y + 52, 0x88000000);

        // 标题
        gui.drawString(mc.font, "§c☢ 辐射系统 §r", x, y + 2, 0xFF5555);

        // 玩家累积辐射值
        String radText = "累积: " + radiation + " / 500";
        int color = getRadiationColor(radiation);
        gui.drawString(mc.font, radText, x, y + 14, color);

        // 累积辐射进度条
        int barWidth = (int)((float)radiation / 500 * 100);
        gui.fill(x, y + 26, x + barWidth, y + 30, getRadiationColor(radiation));
        gui.fill(x + barWidth, y + 26, x + 100, y + 30, 0x44333333);

        // 环境辐射强度（带颜色）
        String envText;
        int envColor;
        if (envRadiation > 0) {
            envText = "环境: " + envRadiation;
            if (envRadiation >= 80) envColor = 0xFF0000;
            else if (envRadiation >= 50) envColor = 0xFF8800;
            else if (envRadiation >= 20) envColor = 0xFFFF00;
            else envColor = 0x00FF00;
        } else {
            envText = "环境: 安全";
            envColor = 0x00AA00;
        }
        gui.drawString(mc.font, envText, x, y + 38, envColor);

        // 警告提示（环境辐射过高时）
        if (envRadiation > 60) {
            gui.drawString(mc.font, "§c⚠ 危险辐射区 ⚠", x + 50, y + 2, 0xFF0000);
        }
    }

    private static int getRadiationColor(int radiation) {
        if (radiation >= 400) return 0xFF0000;      // 红
        if (radiation >= 250) return 0xFF8800;      // 橙
        if (radiation >= 100) return 0xFFFF00;      // 黄
        return 0x00FF00;                             // 绿
    }
}