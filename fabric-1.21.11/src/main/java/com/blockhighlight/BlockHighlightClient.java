package com.blockhighlight;

import com.blockhighlight.client.BlockHighlightRenderer;
import com.blockhighlight.client.ClientSetup;
import com.blockhighlight.client.MobHighlightRenderer;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlockHighlightClient implements ClientModInitializer {
    public static final String MODID = "blockhighlight";
    private static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("Block Highlight mod loaded! Press G for blocks, H for mobs.");
        ClientSetup.init();
        BlockHighlightRenderer.init();
        MobHighlightRenderer.init();
    }
}
