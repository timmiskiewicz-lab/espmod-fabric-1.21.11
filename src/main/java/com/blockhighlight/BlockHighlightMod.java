package com.blockhighlight;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import com.blockhighlight.client.ClientSetup;

@Mod(value = BlockHighlightMod.MODID, dist = Dist.CLIENT)
public class BlockHighlightMod {
    public static final String MODID = "blockhighlight";
    private static final Logger LOGGER = LogUtils.getLogger();

    public BlockHighlightMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Block Highlight mod loaded! Press G to open block selection GUI.");
        ClientSetup.init(modEventBus);
    }
}
