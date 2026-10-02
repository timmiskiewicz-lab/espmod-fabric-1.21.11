package com.blockhighlight.client;

import com.blockhighlight.client.gui.BlockSelectScreen;
import com.blockhighlight.client.gui.MobSelectScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

public class ClientSetup {

    public static void init() {
        KeyBindingHelper.registerKeyBinding(KeyBindings.OPEN_BLOCK_GUI);
        KeyBindingHelper.registerKeyBinding(KeyBindings.OPEN_MOB_GUI);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            while (KeyBindings.OPEN_BLOCK_GUI.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new BlockSelectScreen());
                }
            }

            while (KeyBindings.OPEN_MOB_GUI.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new MobSelectScreen());
                }
            }
        });
    }
}
