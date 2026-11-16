package dev.no.name.blooddesigner.compat;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.renderer.texture.DynamicTexture;

import java.util.function.Supplier;

/**
 * Текстура картинки из переписки. Ванильная DynamicTexture берёт фильтр «ближайший пиксель» — для пиксель-арта,
 * а фото и скриншоты при растяжении превращаются в лесенку. Здесь фильтр линейный, края не повторяются
 */
public final class SmoothTexture extends DynamicTexture {
    public SmoothTexture(Supplier<String> label, NativeImage pixels) {
        super(label, pixels);
        this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
    }
}
