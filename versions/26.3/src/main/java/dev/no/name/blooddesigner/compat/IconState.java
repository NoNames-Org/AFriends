package dev.no.name.blooddesigner.compat;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.no.name.blooddesigner.gui.render.icon.Icon;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2fc;

record IconState(
        Matrix3x2fc pose,
        Icon icon,
        float x,
        float y,
        float size,
        int color,
        float glow,
        TextureSetup textureSetup,
        ScreenRectangle scissorArea,
        ScreenRectangle bounds
) implements GuiElementRenderState {
    private static final float GLOW_SCALE = 256;

    @Override
    public void buildVertices(VertexConsumer vertices) {
        float margin = Icon.margin(size);
        float left = x - margin;
        float top = y - margin;
        float right = x + size + margin;
        float bottom = y + size + margin;

        addVertex(vertices, left, top, icon.getU0(), icon.getV0());
        addVertex(vertices, left, bottom, icon.getU0(), icon.getV1());
        addVertex(vertices, right, bottom, icon.getU1(), icon.getV1());
        addVertex(vertices, right, top, icon.getU1(), icon.getV0());
    }

    private void addVertex(VertexConsumer vertices, float vertexX, float vertexY, float u, float v) {
        vertices.addVertexWith2DPose(pose, vertexX, vertexY)
                .setColor(color)
                .setUv(u, v)

                .setUv2(Icon.ATLAS_SPREAD, Math.round(glow * GLOW_SCALE));
    }

    @Override
    public RenderPipeline pipeline() {
        return MenuPipelines.ICON;
    }
}
