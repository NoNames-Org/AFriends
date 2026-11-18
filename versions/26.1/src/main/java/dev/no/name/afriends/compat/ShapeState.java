package dev.no.name.afriends.compat;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2fc;

record ShapeState(
        Matrix3x2fc pose,
        float x,
        float y,
        float width,
        float height,
        float radius,
        float stroke,
        float softness,
        int startColor,
        int endColor,
        boolean horizontal,
        ScreenRectangle scissorArea,
        ScreenRectangle bounds
) implements GuiElementRenderState {
    private static final float SCALE = 64;
    private static final int SOFT_FLAG = 16384;

    @Override
    public void buildVertices(VertexConsumer vertices) {
        float halfWidth = width / 2;
        float halfHeight = height / 2;
        int bottomLeft = horizontal ? startColor : endColor;
        int topRight = horizontal ? endColor : startColor;

        addVertex(vertices, x, y, -halfWidth, -halfHeight, startColor);
        addVertex(vertices, x, y + height, -halfWidth, halfHeight, bottomLeft);
        addVertex(vertices, x + width, y + height, halfWidth, halfHeight, endColor);
        addVertex(vertices, x + width, y, halfWidth, -halfHeight, topRight);
    }

    private void addVertex(VertexConsumer vertices, float vertexX, float vertexY, float localX, float localY, int color) {
        int packedRadius = Math.round(radius * SCALE);
        int packedExtra = softness > 0 ? SOFT_FLAG + Math.round(softness * SCALE) : Math.round(stroke * SCALE);
        vertices.addVertexWith2DPose(pose, vertexX, vertexY)
                .setColor(color)
                .setUv(localX, localY)
                .setUv2(packedRadius, packedExtra);
    }

    @Override
    public RenderPipeline pipeline() {
        return MenuPipelines.SHAPE;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }
}
