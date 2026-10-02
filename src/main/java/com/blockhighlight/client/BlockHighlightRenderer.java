package com.blockhighlight.client;

import com.blockhighlight.BlockHighlightMod;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.*;

@EventBusSubscriber(modid = BlockHighlightMod.MODID, value = Dist.CLIENT)
public class BlockHighlightRenderer {

    public static final RenderPipeline LINES_NO_DEPTH = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(Identifier.parse(BlockHighlightMod.MODID + ":pipeline/lines_no_depth"))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .build();

    private static final RenderType LINES_THROUGH_WALLS = RenderType.create(
            BlockHighlightMod.MODID + ":lines_through_walls",
            RenderSetup.builder(LINES_NO_DEPTH).createRenderSetup()
    );

    private static final Set<Block> selectedBlocks = Collections.synchronizedSet(new LinkedHashSet<>());
    private static volatile List<BlockPos> highlightedPositions = Collections.emptyList();

    private static final int CHUNK_SIZE = 16;
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

    private static final ExecutorService SCAN_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "BlockHighlight-Scanner");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private static volatile boolean scanInFlight = false;

    public static Set<Block> getSelectedBlocks() {
        return selectedBlocks;
    }

    public static void toggleBlock(Block block) {
        if (!selectedBlocks.remove(block)) {
            selectedBlocks.add(block);
        }
        currentSelectionVersion++;
        scanCooldown = 0;
    }

    public static void clearAll() {
        selectedBlocks.clear();
        highlightedPositions = Collections.emptyList();
        currentSelectionVersion++;
    }

    public static void selectAll(Collection<Block> blocks) {
        selectedBlocks.addAll(blocks);
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

    public static void onRegisterPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(LINES_NO_DEPTH);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (scanCooldown > 0) {
            scanCooldown--;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || selectedBlocks.isEmpty()) {
            if (!highlightedPositions.isEmpty()) highlightedPositions = Collections.emptyList();
            return;
        }

        if (scanInFlight) return;

        BlockPos playerPos = mc.player.blockPosition();
        long playerChunkKey = ((long)(playerPos.getX() >> 4)) << 32 | ((playerPos.getZ() >> 4) & 0xFFFFFFFFL);
        boolean playerMovedChunk = playerChunkKey != lastPlayerChunkKey;
        boolean selectionChanged = currentSelectionVersion != lastSelectionVersion;

        if (scanCooldown > 0 && !selectionChanged && !playerMovedChunk) return;
        scanCooldown = SCAN_INTERVAL_TICKS;

        lastSelectionVersion = currentSelectionVersion;
        lastPlayerChunkKey = playerChunkKey;

        ClientLevel level = mc.level;
        Set<Block> snapshot;
        synchronized (selectedBlocks) {
            snapshot = new HashSet<>(selectedBlocks);
        }
        int range = scanRangeChunks;
        int playerCX = playerPos.getX() >> 4;
        int playerCZ = playerPos.getZ() >> 4;

        scanInFlight = true;
        SCAN_EXECUTOR.submit(() -> {
            try {
                List<BlockPos> result = scanChunks(level, snapshot, playerCX, playerCZ, range);
                highlightedPositions = result;
            } catch (Throwable t) {
                highlightedPositions = Collections.emptyList();
            } finally {
                scanInFlight = false;
            }
        });
    }

    private static List<BlockPos> scanChunks(ClientLevel level, Set<Block> selected,
                                             int playerCX, int playerCZ, int range) {
        List<BlockPos> found = new ArrayList<>(256);
        int minSectionY = level.getMinSectionY();

        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                int cx = playerCX + dx;
                int cz = playerCZ + dz;

                LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) continue;

                LevelChunkSection[] sections = chunk.getSections();
                if (sections == null) continue;

                int chunkBaseX = cx << 4;
                int chunkBaseZ = cz << 4;

                for (int sectionIdx = 0; sectionIdx < sections.length; sectionIdx++) {
                    LevelChunkSection section = sections[sectionIdx];
                    if (section == null || section.hasOnlyAir()) continue;

                    boolean possiblyContains = section.maybeHas(s -> selected.contains(s.getBlock()));
                    if (!possiblyContains) continue;

                    int sectionBaseY = (minSectionY + sectionIdx) << 4;

                    for (int ly = 0; ly < 16; ly++) {
                        for (int lz = 0; lz < 16; lz++) {
                            for (int lx = 0; lx < 16; lx++) {
                                BlockState state = section.getBlockState(lx, ly, lz);
                                if (selected.contains(state.getBlock())) {
                                    found.add(new BlockPos(chunkBaseX + lx, sectionBaseY + ly, chunkBaseZ + lz));
                                }
                            }
                        }
                    }
                }
            }
        }

        return found;
    }

    @SubscribeEvent
    public static void onRenderAfterTranslucent(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        List<BlockPos> positions = highlightedPositions;
        if (positions.isEmpty()) return;

        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = event.getPoseStack();
        PoseStack.Pose pose = poseStack.last();

        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(LINES_THROUGH_WALLS);

        double cx = camera.x();
        double cy = camera.y();
        double cz = camera.z();

        for (BlockPos pos : positions) {
            float x1 = (float) (pos.getX() - cx);
            float y1 = (float) (pos.getY() - cy);
            float z1 = (float) (pos.getZ() - cz);
            float x2 = x1 + 1.0f;
            float y2 = y1 + 1.0f;
            float z2 = z1 + 1.0f;
            renderBox(consumer, pose, x1, y1, z1, x2, y2, z2);
        }

        bufferSource.endBatch(LINES_THROUGH_WALLS);
    }

    private static void renderBox(VertexConsumer consumer, PoseStack.Pose pose,
                                  float x1, float y1, float z1, float x2, float y2, float z2) {
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
