package com.example.addon.modules.Hypixel.DungeonRoomsHelper.Class;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.Renderer3D;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class WaypointUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    /**
     * Draw a full 1x1x1 block highlight.
     */
    public static void drawFilledBoundingBox(Box box, Color color, float alpha, Render3DEvent event) {
        Renderer3D r = event.renderer;

        Color side = new Color(color.r, color.g, color.b, (int)(alpha * 255));
        Color line = new Color(color.r, color.g, color.b, 255);

        r.box(box, side, line, ShapeMode.Both, 0);
    }

    /**
     * Draw a vertical “beacon beam”.
     */
    public static void renderBeaconBeam(Render3DEvent event, BlockPos pos, Color color, float alpha) {
        Renderer3D r = event.renderer;

        Box pillar = new Box(
            pos.getX() + 0.3, pos.getY(),
            pos.getZ() + 0.3,
            pos.getX() + 0.7, pos.getY() + 25,
            pos.getZ() + 0.7
        );

        Color side = new Color(color.r, color.g, color.b, (int)(alpha * 255));
        Color line = new Color(color.r, color.g, color.b, 255);

        r.box(pillar, side, line, ShapeMode.Sides, 0);
    }

    /**
     * Floating text above a waypoint (name + distance).
     */
    public static void renderWaypointText(Render3DEvent event, BlockPos pos, String text, Color color) {
        MinecraftClient mc = MinecraftClient.getInstance();
        MatrixStack matrices = event.matrices;
        TextRenderer tr = mc.textRenderer;

        Vec3d cam = mc.gameRenderer.getCamera().getPos();

        double wx = pos.getX() + 0.5;
        double wy = pos.getY() + 1.4;
        double wz = pos.getZ() + 0.5;

        matrices.push();
        matrices.translate(wx - cam.x, wy - cam.y, wz - cam.z);

        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-mc.gameRenderer.getCamera().getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(mc.gameRenderer.getCamera().getPitch()));

        float scale = 0.025f;
        matrices.scale(-scale, -scale, scale);

        OrderedText ordered = Text.literal(text).asOrderedText();

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        VertexConsumerProvider consumers =
            MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();

        tr.draw(
            ordered,
            -tr.getWidth(ordered) / 2f,
            0,
            color.getPacked(),
            false,
            matrix,
            consumers,
            TextRenderer.TextLayerType.NORMAL,
            0,
            0xF000F0
        );

        matrices.pop();
    }



}
