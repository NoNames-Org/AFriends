package dev.no.name.blooddesigner.compat;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.no.name.blooddesigner.BloodDesigner;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

final class MenuPipelines {
    public static final RenderPipeline SHAPE = RenderPipelines.register(menuQuad("shape").build());

    public static final RenderPipeline ICON = RenderPipelines.register(menuQuad("icon").withSampler("Sampler0").build());

    public static final RenderPipeline BACKDROP = RenderPipelines.register(menuQuad("backdrop").build());

    private MenuPipelines() {
    }

    public static void init() {
    }

    private static RenderPipeline.Builder menuQuad(String fragmentShader) {
        return RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(id("pipeline/" + fragmentShader))
                .withVertexShader(id("core/menu_quad"))
                .withFragmentShader(id("core/" + fragmentShader))
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(BloodDesigner.MOD_ID, path);
    }
}
