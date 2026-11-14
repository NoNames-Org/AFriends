package dev.no.name.blooddesigner.compat;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2fc;

record BackdropState(
        Matrix3x2fc pose,
        float width,
        float height,
        int color,
        float vignette,
        float grain,
        ScreenRectangle scissorArea,
        ScreenRectangle bounds
) implements GuiElementRenderState {
    private static final float PARAM_SCALE = 256;

    @Override
    public void buildVertices(VertexConsumer vertices) {
        addVertex(vertices, 0, 0, 0, 0);
        addVertex(vertices, 0, height, 0, 1);
        addVertex(vertices, width, height, 1, 1);
        addVertex(vertices, width, 0, 1, 0);
    }

    private void addVertex(VertexConsumer vertices, float vertexX, float vertexY, float u, float v) {
        vertices.addVertexWith2DPose(pose, vertexX, vertexY)
                .setColor(color)
                .setUv(u, v)
                .setUv2(Math.round(vignette * PARAM_SCALE), Math.round(grain * PARAM_SCALE));
    }

    @Override
    public RenderPipeline pipeline() {
        return MenuPipelines.BACKDROP;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }
}
