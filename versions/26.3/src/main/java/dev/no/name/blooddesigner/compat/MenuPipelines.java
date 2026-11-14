package dev.no.name.blooddesigner.compat;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.no.name.blooddesigner.BloodDesigner;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

final class MenuPipelines {
    public static final RenderPipeline SHAPE = RenderPipelines.register(menuQuad("shape").build());

    public static final RenderPipeline ICON = RenderPipelines.register(
            menuQuad("icon").withBindGroupLayout(BindGroupLayouts.SAMPLER0).build());

    public static final RenderPipeline BACKDROP = RenderPipelines.register(menuQuad("backdrop").build());

    private MenuPipelines() {
    }

    public static void init() {
    }

    private static RenderPipeline.Builder menuQuad(String fragmentShader) {
        return RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withLocation(id("pipeline/" + fragmentShader))
                .withVertexShader(id("core/menu_quad"))
                .withFragmentShader(id("core/" + fragmentShader))
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP)
                .withPrimitiveTopology(PrimitiveTopology.QUADS);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(BloodDesigner.MOD_ID, path);
    }
}
