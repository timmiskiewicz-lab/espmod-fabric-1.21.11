package com.blockhighlight.client;

import com.blockhighlight.BlockHighlightMod;
import com.blockhighlight.client.gui.BlockSelectScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

public class ClientSetup {

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(RegisterKeyMappingsEvent.class, ClientSetup::onRegisterKeyMappings);
        modEventBus.addListener(RegisterRenderPipelinesEvent.class, BlockHighlightRenderer::onRegisterPipelines);
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.OPEN_GUI);
    }

    @EventBusSubscriber(modid = BlockHighlightMod.MODID, value = Dist.CLIENT)
    public static class GameEvents {

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            while (KeyBindings.OPEN_GUI.consumeClick()) {
                if (mc.screen == null) {
                    mc.setScreen(new BlockSelectScreen());
                }
            }
        }
    }
}
