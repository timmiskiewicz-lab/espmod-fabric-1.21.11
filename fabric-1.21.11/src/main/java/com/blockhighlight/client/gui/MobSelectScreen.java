package com.blockhighlight.client.gui;

import com.blockhighlight.client.MobHighlightRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class MobSelectScreen extends Screen {

    private EditBox searchBox;
    private EditBox rangeBox;
    private MobListWidget mobList;
    private final List<EntityType<?>> allMobTypes;

    public MobSelectScreen() {
        super(Component.literal("Mob Highlight - Press H to close"));
        List<EntityType<?>> types = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (type == EntityType.PLAYER) continue;
            if (SpawnEggItem.byId(type) == null) continue;
            types.add(type);
        }
        types.sort(Comparator.comparing(t -> safeName(t).toLowerCase(Locale.ROOT)));
        this.allMobTypes = types;
    }

    private static String safeName(EntityType<?> type) {
        try {
            String name = type.getDescription().getString();
            if (name == null || name.isEmpty()) {
                return BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
            }
            return name;
        } catch (Exception e) {
            return BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        }
    }

    private static ItemStack iconFor(EntityType<?> type) {
        SpawnEggItem egg = SpawnEggItem.byId(type);
        return egg != null ? new ItemStack(egg) : ItemStack.EMPTY;
    }

    @Override
    protected void init() {
        int listTop = 70;
        int listBottom = this.height - 50;
        int listHeight = listBottom - listTop;

        mobList = new MobListWidget(this.minecraft, this.width, listHeight, listTop, 18);
        this.addRenderableWidget(mobList);

        searchBox = new EditBox(this.font, this.width / 2 - 160, 20, 250, 18, Component.literal("Search"));
        searchBox.setMaxLength(128);
        searchBox.setHint(Component.literal("Type to search mobs..."));
        searchBox.setResponder(text -> refreshList());
        this.addRenderableWidget(searchBox);
        this.setInitialFocus(searchBox);

        rangeBox = new EditBox(this.font, this.width / 2 + 100, 20, 60, 18, Component.literal("Range"));
        rangeBox.setMaxLength(3);
        rangeBox.setHint(Component.literal("chunks"));
        rangeBox.setValue(String.valueOf(MobHighlightRenderer.getScanRangeChunks()));
        rangeBox.setFilter(s -> s.isEmpty() || s.matches("\\d{1,3}"));
        rangeBox.setResponder(this::onRangeChanged);
        this.addRenderableWidget(rangeBox);

        this.addRenderableWidget(Button.builder(Component.literal("Select Visible"), btn -> {
            MobHighlightRenderer.selectAll(getFilteredMobTypes());
        }).bounds(this.width / 2 - 154, this.height - 28, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Clear All"), btn -> {
            MobHighlightRenderer.clearAll();
        }).bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Done"), btn -> onClose())
                .bounds(this.width / 2 + 54, this.height - 28, 100, 20).build());

        refreshList();
    }

    private void onRangeChanged(String text) {
        if (text.isEmpty()) return;
        try {
            int chunks = Integer.parseInt(text);
            MobHighlightRenderer.setScanRangeChunks(chunks);
        } catch (NumberFormatException ignored) {
        }
    }

    private List<EntityType<?>> getFilteredMobTypes() {
        String query = searchBox == null ? "" : searchBox.getValue().toLowerCase(Locale.ROOT).trim();
        if (query.isEmpty()) {
            return allMobTypes;
        }
        return allMobTypes.stream()
                .filter(t -> safeName(t).toLowerCase(Locale.ROOT).contains(query)
                        || BuiltInRegistries.ENTITY_TYPE.getKey(t).toString().toLowerCase(Locale.ROOT).contains(query))
                .collect(Collectors.toList());
    }

    private void refreshList() {
        if (mobList == null) return;
        mobList.clearEntries();
        for (EntityType<?> type : getFilteredMobTypes()) {
            mobList.addEntry(new MobListWidget.MobEntry(type, this.font));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 6, 0xFFFFFFFF);

        graphics.drawString(this.font, "Search:", this.width / 2 - 200, 26, 0xFFCCCCCC);
        graphics.drawString(this.font, "Range:", this.width / 2 + 65, 26, 0xFFCCCCCC);
        graphics.drawString(this.font, "chunks (" + MobHighlightRenderer.getMinChunkRange()
                        + "-" + MobHighlightRenderer.getMaxChunkRange() + ")",
                this.width / 2 + 165, 26, 0xFF888888);

        int totalLoaded = allMobTypes.size();
        int displayed = mobList == null ? 0 : mobList.getItemCountPublic();
        int selected = MobHighlightRenderer.getSelectedMobTypes().size();
        int rangeChunks = MobHighlightRenderer.getScanRangeChunks();
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

    public static class MobListWidget extends ObjectSelectionList<MobListWidget.MobEntry> {

        public MobListWidget(Minecraft mc, int width, int height, int y, int itemHeight) {
            super(mc, width, height, y, itemHeight);
        }

        @Override
        public void clearEntries() {
            super.clearEntries();
        }

        @Override
        public int addEntry(MobEntry entry) {
            return super.addEntry(entry);
        }

        public int getItemCountPublic() {
            return this.children().size();
        }

        @Override
        public int getRowWidth() {
            return Math.min(380, this.width - 40);
        }

        public static class MobEntry extends ObjectSelectionList.Entry<MobEntry> {
            private final EntityType<?> mobType;
            private final net.minecraft.client.gui.Font font;
            private final String displayName;
            private final ItemStack iconStack;

            public MobEntry(EntityType<?> mobType, net.minecraft.client.gui.Font font) {
                this.mobType = mobType;
                this.font = font;
                this.displayName = safeName(mobType);
                this.iconStack = iconFor(mobType);
            }

            @Override
            public Component getNarration() {
                return Component.literal(displayName);
            }

            @Override
            public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean isHovered, float partialTick) {
                boolean selected = MobHighlightRenderer.getSelectedMobTypes().contains(mobType);
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
                if (!iconStack.isEmpty()) {
                    graphics.renderFakeItem(iconStack, iconX, iconY);
                }

                String checkbox = selected ? "[X] " : "[ ] ";
                graphics.drawString(font, checkbox + displayName,
                        iconX + (iconStack.isEmpty() ? 0 : 20), y + (h - 8) / 2, color);
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent mouseEvent, boolean isInside) {
                if (isInside && mouseEvent.button() == 0) {
                    MobHighlightRenderer.toggleMobType(mobType);
                    return true;
                }
                return super.mouseClicked(mouseEvent, isInside);
            }
        }
    }
}
