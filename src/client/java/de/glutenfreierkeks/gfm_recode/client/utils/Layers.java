package de.glutenfreierkeks.gfm_recode.client.utils;

import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.RenderSetup;

public final class Layers {
    private Layers() {
    }

    public static final RenderLayer TRACERS = RenderLayer.of(
        "tracers",
        RenderSetup.builder(
                RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withLocation("pipeline/tracer_lines_no_depth")
                    .build()
            )
            .build()
    );
}
