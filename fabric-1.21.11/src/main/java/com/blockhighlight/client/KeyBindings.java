package com.blockhighlight.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class KeyBindings {
    public static final KeyMapping OPEN_BLOCK_GUI = new KeyMapping(
            "key.blockhighlight.open_block_gui",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            KeyMapping.Category.MISC
    );

    public static final KeyMapping OPEN_MOB_GUI = new KeyMapping(
            "key.blockhighlight.open_mob_gui",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            KeyMapping.Category.MISC
    );
}
