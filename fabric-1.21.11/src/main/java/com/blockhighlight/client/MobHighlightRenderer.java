package com.blockhighlight.client;

import com.blockhighlight.BlockHighlightClient;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Environment(EnvType.CLIENT)
public class MobHighlightRenderer {

    public static final RenderPipeline LINES_NO_DEPTH = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                    .withLocation(Identifier.parse(BlockHighlightClient.MODID + ":pipeline/mob_lines_no_depth"))
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .build());

    private static final RenderType LINES_THROUGH_WALLS = RenderType.create(
            BlockHighlightClient.MODID + ":mob_lines_through_walls",
            RenderSetup.builder(LINES_NO_DEPTH).createRenderSetup()
    );

    private static final Set<EntityType<?>> selectedMobTypes = Collections.synchronizedSet(new LinkedHashSet<>());
    private static volatile List<AABB> highlightedBoxes = Collections.emptyList();

    private static final int MIN_CHUNK_RANGE = 1;
    private static final int MAX_CHUNK_RANGE = 16;
    private static volatile int scanRangeChunks = 2;

    private static final int HIGHLIGHT_COLOR = 0xFFFF0000;
    private static final float LINE_WIDTH = 4.0f;

    private static final int SCAN_INTERVAL_TICKS = 20;
    private static int scanCooldown = 0;
    private static long lastSelectionVersion = -1;
    private static long currentSelectionVersion = 0;
    private static long lastPlayerChunkKey = Long.MIN_VALUE;

    public static Set<EntityType<?>> getSelectedMobTypes() {
        return selectedMobTypes;
    }

    public static void toggleMobType(EntityType<?> type) {
        if (!selectedMobTypes.remove(type)) {
            selectedMobTypes.add(type);
        }
        currentSelectionVersion++;
        scanCooldown = 0;
    }

    public static void clearAll() {
        selectedMobTypes.clear();
        highlightedBoxes = Collections.emptyList();
        currentSelectionVersion++;
    }

    public static void selectAll(Collection<EntityType<?>> types) {
        selectedMobTypes.addAll(types);
        currentSelectionVersion++;
        scanCooldown = 0;
    }

    public static int getScanRangeChunks() {
        return scanRangeChunks;
    }

    public static void setScanRangeChunks(int chunks) {
        int clamped = Math.max(MIN_CHUNK_RANGE, Math.min(MAX_CHUNK_RANGE, chunks));
        if (clamped != scanRangeChunks) {
            scanRangeChunks = clamped;
            currentSelectionVersion++;
            scanCooldown = 0;
        }
    }

    public static int getMinChunkRange() {
        return MIN_CHUNK_RANGE;
    }

    public static int getMaxChunkRange() {
        return MAX_CHUNK_RANGE;
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(MobHighlightRenderer::onClientTick);
        WorldRenderEvents.END_MAIN.register(MobHighlightRenderer::onRenderAfterTranslucent);
    }

    private static void onClientTick(Minecraft mc) {
        if (scanCooldown > 0) {
            scanCooldown--;
        }

        if (mc.player == null || mc.level == null || selectedMobTypes.isEmpty()) {
            if (!highlightedBoxes.isEmpty()) highlightedBoxes = Collections.emptyList();
            return;
        }

        BlockPos playerPos = mc.player.blockPosition();
        long playerChunkKey = ((long) (playerPos.getX() >> 4)) << 32 | ((playerPos.getZ() >> 4) & 0xFFFFFFFFL);
        boolean playerMovedChunk = playerChunkKey != lastPlayerChunkKey;
        boolean selectionChanged = currentSelectionVersion != lastSelectionVersion;

        if (scanCooldown > 0 && !selectionChanged && !playerMovedChunk) return;
        scanCooldown = SCAN_INTERVAL_TICKS;

        lastSelectionVersion = currentSelectionVersion;
        lastPlayerChunkKey = playerChunkKey;

        Set<EntityType<?>> snapshot;
        synchronized (selectedMobTypes) {
            snapshot = Set.copyOf(selectedMobTypes);
        }

        highlightedBoxes = scanMobs(mc.level, mc.player, snapshot, scanRangeChunks);
    }

    private static List<AABB> scanMobs(ClientLevel level, LocalPlayer player,
                                         Set<EntityType<?>> selected, int rangeChunks) {
        double range = rangeChunks * 16.0;
        AABB searchBox = player.getBoundingBox().inflate(range, range, range);
        List<AABB> found = new ArrayList<>(64);

        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, searchBox,
                e -> e != player && selected.contains(e.getType()))) {
            found.add(entity.getBoundingBox());
        }

        return found;
    }

    private static void onRenderAfterTranslucent(WorldRenderContext context) {
        List<AABB> boxes = highlightedBoxes;
        if (boxes.isEmpty()) return;

        PoseStack poseStack = context.matrices();
        if (poseStack == null) return;

        Vec3 camera = context.worldState().cameraRenderState.pos;
        PoseStack.Pose pose = poseStack.last();

        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(LINES_THROUGH_WALLS);

        double cx = camera.x();
        double cy = camera.y();
        double cz = camera.z();

        for (AABB box : boxes) {
            renderBox(consumer, pose, cx, cy, cz, box);
        }

        bufferSource.endBatch(LINES_THROUGH_WALLS);
    }

    private static void renderBox(VertexConsumer consumer, PoseStack.Pose pose,
                                  double cx, double cy, double cz, AABB box) {
        float x1 = (float) (box.minX - cx);
        float y1 = (float) (box.minY - cy);
        float z1 = (float) (box.minZ - cz);
        float x2 = (float) (box.maxX - cx);
        float y2 = (float) (box.maxY - cy);
        float z2 = (float) (box.maxZ - cz);

        addLine(consumer, pose, x1, y1, z1, x2, y1, z1);
        addLine(consumer, pose, x2, y1, z1, x2, y1, z2);
        addLine(consumer, pose, x2, y1, z2, x1, y1, z2);
        addLine(consumer, pose, x1, y1, z2, x1, y1, z1);
        addLine(consumer, pose, x1, y2, z1, x2, y2, z1);
        addLine(consumer, pose, x2, y2, z1, x2, y2, z2);
        addLine(consumer, pose, x2, y2, z2, x1, y2, z2);
        addLine(consumer, pose, x1, y2, z2, x1, y2, z1);
        addLine(consumer, pose, x1, y1, z1, x1, y2, z1);
        addLine(consumer, pose, x2, y1, z1, x2, y2, z1);
        addLine(consumer, pose, x2, y1, z2, x2, y2, z2);
        addLine(consumer, pose, x1, y1, z2, x1, y2, z2);
    }

    private static void addLine(VertexConsumer consumer, PoseStack.Pose pose,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2) {
        Vector3f normal = new Vector3f(x2 - x1, y2 - y1, z2 - z1).normalize();
        consumer.addVertex(pose, x1, y1, z1).setColor(HIGHLIGHT_COLOR).setNormal(pose, normal).setLineWidth(LINE_WIDTH);
        consumer.addVertex(pose, x2, y2, z2).setColor(HIGHLIGHT_COLOR).setNormal(pose, normal).setLineWidth(LINE_WIDTH);
    }
}
