package com.blockhighlight.client.gui;

import com.blockhighlight.client.BlockHighlightRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class BlockSelectScreen extends Screen {

    private EditBox searchBox;
    private EditBox rangeBox;
    private BlockListWidget blockList;
    private final List<Block> allBlocks;

    public BlockSelectScreen() {
        super(Component.literal("Block Highlight - Press G to close"));
        List<Block> blocks = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR) continue;
            blocks.add(block);
        }
        blocks.sort(Comparator.comparing(b -> safeName(b).toLowerCase(Locale.ROOT)));
        this.allBlocks = blocks;
    }

    private static String safeName(Block block) {
        try {
            String name = block.getName().getString();
            if (name == null || name.isEmpty()) {
                return BuiltInRegistries.BLOCK.getKey(block).toString();
            }
            return name;
        } catch (Exception e) {
            return BuiltInRegistries.BLOCK.getKey(block).toString();
        }
    }

    @Override
    protected void init() {
        int listTop = 70;
        int listBottom = this.height - 50;
        int listHeight = listBottom - listTop;

        blockList = new BlockListWidget(this.minecraft, this.width, listHeight, listTop, 18);
        this.addRenderableWidget(blockList);

        searchBox = new EditBox(this.font, this.width / 2 - 160, 20, 250, 18, Component.literal("Search"));
        searchBox.setMaxLength(128);
        searchBox.setHint(Component.literal("Type to search blocks..."));
        searchBox.setResponder(text -> refreshList());
        this.addRenderableWidget(searchBox);
        this.setInitialFocus(searchBox);

        rangeBox = new EditBox(this.font, this.width / 2 + 100, 20, 60, 18, Component.literal("Range"));
        rangeBox.setMaxLength(3);
        rangeBox.setHint(Component.literal("chunks"));
        rangeBox.setValue(String.valueOf(BlockHighlightRenderer.getScanRangeChunks()));
        rangeBox.setFilter(s -> s.isEmpty() || s.matches("\\d{1,3}"));
        rangeBox.setResponder(this::onRangeChanged);
        this.addRenderableWidget(rangeBox);

        this.addRenderableWidget(Button.builder(Component.literal("Select Visible"), btn -> {
            BlockHighlightRenderer.selectAll(getFilteredBlocks());
        }).bounds(this.width / 2 - 154, this.height - 28, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Clear All"), btn -> {
            BlockHighlightRenderer.clearAll();
        }).bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Done"), btn -> onClose())
                .bounds(this.width / 2 + 54, this.height - 28, 100, 20).build());

        refreshList();
    }

    private void onRangeChanged(String text) {
        if (text.isEmpty()) return;
        try {
            int chunks = Integer.parseInt(text);
            BlockHighlightRenderer.setScanRangeChunks(chunks);
        } catch (NumberFormatException ignored) {
        }
    }

    private List<Block> getFilteredBlocks() {
        String query = searchBox == null ? "" : searchBox.getValue().toLowerCase(Locale.ROOT).trim();
        if (query.isEmpty()) {
            return allBlocks;
        }
        return allBlocks.stream()
                .filter(b -> safeName(b).toLowerCase(Locale.ROOT).contains(query)
                        || BuiltInRegistries.BLOCK.getKey(b).toString().toLowerCase(Locale.ROOT).contains(query))
                .collect(Collectors.toList());
    }

    private void refreshList() {
        if (blockList == null) return;
        blockList.clearEntries();
        List<Block> filtered = getFilteredBlocks();
        for (Block block : filtered) {
            blockList.addEntry(new BlockListWidget.BlockEntry(block, this.font));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 6, 0xFFFFFFFF);

        graphics.drawString(this.font, "Search:", this.width / 2 - 200, 26, 0xFFCCCCCC);
        graphics.drawString(this.font, "Range:", this.width / 2 + 65, 26, 0xFFCCCCCC);
        graphics.drawString(this.font, "chunks (" + BlockHighlightRenderer.getMinChunkRange()
                        + "-" + BlockHighlightRenderer.getMaxChunkRange() + ")",
                this.width / 2 + 165, 26, 0xFF888888);

        int totalLoaded = allBlocks.size();
        int displayed = blockList == null ? 0 : blockList.getItemCountPublic();
        int selected = BlockHighlightRenderer.getSelectedBlocks().size();
        int rangeChunks = BlockHighlightRenderer.getScanRangeChunks();
        int rangeBlocks = rangeChunks * 16;
        String info = "Loaded: " + totalLoaded + "  |  Showing: " + displayed
                + "  |  Selected: " + selected
                + "  |  Range: " + rangeChunks + " chunks (" + rangeBlocks + " blocks)";
        graphics.drawCenteredString(this.font, info, this.width / 2, this.height - 44, 0xFFAAAAAA);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public static class BlockListWidget extends ObjectSelectionList<BlockListWidget.BlockEntry> {

        public BlockListWidget(Minecraft mc, int width, int height, int y, int itemHeight) {
            super(mc, width, height, y, itemHeight);
        }

        @Override
        public void clearEntries() {
            super.clearEntries();
        }

        @Override
        public int addEntry(BlockEntry entry) {
            return super.addEntry(entry);
        }

        public int getItemCountPublic() {
            return this.children().size();
        }

        @Override
        public int getRowWidth() {
            return Math.min(380, this.width - 40);
        }

        public static class BlockEntry extends ObjectSelectionList.Entry<BlockEntry> {
            private final Block block;
            private final net.minecraft.client.gui.Font font;
            private final String displayName;
            private final ItemStack iconStack;

            public BlockEntry(Block block, net.minecraft.client.gui.Font font) {
                this.block = block;
                this.font = font;
                this.displayName = safeName(block);
                this.iconStack = new ItemStack(block);
            }

            @Override
            public Component getNarration() {
                return Component.literal(displayName);
            }

            @Override
            public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean isHovered, float partialTick) {
                boolean selected = BlockHighlightRenderer.getSelectedBlocks().contains(block);
                int color = selected ? 0xFF55FF55 : 0xFFFFFFFF;
                if (isHovered) color = selected ? 0xFF88FF88 : 0xFFFFFF88;

                int x = this.getX();
                int y = this.getY();
                int w = this.getWidth();
                int h = this.getHeight();

                graphics.fill(x, y, x + w, y + h,
                        isHovered ? 0x80333333 : 0x66222222);

                if (selected) {
                    graphics.fill(x, y, x + 2, y + h, 0xFF55FF55);
                }

                int iconX = x + 4;
                int iconY = y + (h - 16) / 2;
                graphics.renderFakeItem(iconStack, iconX, iconY);

                String checkbox = selected ? "[X] " : "[ ] ";
                graphics.drawString(font, checkbox + displayName,
                        iconX + 20, y + (h - 8) / 2, color);
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent mouseEvent, boolean isInside) {
                if (isInside && mouseEvent.button() == 0) {
                    BlockHighlightRenderer.toggleBlock(block);
                    return true;
                }
                return super.mouseClicked(mouseEvent, isInside);
            }
        }
    }
}
