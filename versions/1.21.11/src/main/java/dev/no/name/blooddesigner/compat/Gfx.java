package dev.no.name.blooddesigner.compat;

import dev.no.name.blooddesigner.BloodDesigner;
import dev.no.name.blooddesigner.gui.render.icon.Icon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;

public final class Gfx {
    private static final Identifier ICON_ATLAS =
            Identifier.fromNamespaceAndPath(BloodDesigner.MOD_ID, "textures/gui/icons.png");

    private final GuiGraphics graphics;

    public Gfx(GuiGraphics graphics) {
        this.graphics = graphics;
    }

    public Matrix3x2fStack pose() {
        return graphics.pose();
    }

    public ScreenRectangle scissor() {
        return graphics.scissorStack.peek();
    }

    public void enableScissor(int left, int top, int right, int bottom) {
        graphics.enableScissor(left, top, right, bottom);
    }

    public void disableScissor() {
        graphics.disableScissor();
    }

    public void blurBeforeThisStratum() {
        graphics.blurBeforeThisStratum();
    }

    public void text(Font font, Component text, int x, int y, int color, boolean shadow) {
        graphics.drawString(font, text, x, y, color, shadow);
    }

    public void face(PlayerSkin skin, int x, int y, int size) {
        PlayerFaceRenderer.draw(graphics, skin, x, y, size);
    }

    public void shape(Matrix3x2f pose, float x, float y, float width, float height, float radius, float stroke,
                      float softness, int startColor, int endColor, boolean horizontal,
                      ScreenRectangle scissor, ScreenRectangle bounds) {
        graphics.guiRenderState.submitGuiElement(new ShapeState(pose, x, y, width, height, radius, stroke, softness,
                startColor, endColor, horizontal, scissor, bounds));
    }

    public void backdrop(Matrix3x2f pose, float width, float height, int color, float vignette, float grain,
                         ScreenRectangle scissor, ScreenRectangle bounds) {
        graphics.guiRenderState.submitGuiElement(new BackdropState(pose, width, height, color, vignette, grain, scissor, bounds));
    }

    public void icon(Matrix3x2f pose, Icon icon, float x, float y, float size, int color, float glow,
                     ScreenRectangle scissor, ScreenRectangle bounds) {
        AbstractTexture atlas = Minecraft.getInstance().getTextureManager().getTexture(ICON_ATLAS);
        TextureSetup texture = TextureSetup.singleTexture(atlas.getTextureView(), atlas.getSampler());
        graphics.guiRenderState.submitGuiElement(new IconState(pose, icon, x, y, size, color, glow, texture, scissor, bounds));
    }

    public void nextStratum() {
        graphics.nextStratum();
    }

    public void blit(Identifier texture, int x, int y, int size, int textureSize) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, size, size,
                textureSize, textureSize, textureSize, textureSize);
    }

    // вся текстура textureWidth×textureHeight, растянутая в прямоугольник width×height (картинки в чате)
    public void image(Identifier texture, int x, int y, int width, int height, int textureWidth, int textureHeight) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, width, height,
                textureWidth, textureHeight, textureWidth, textureHeight);
    }

    /** Предмет 16×16 со счётчиком и полоской прочности, как в инвентаре. */
    public void item(Font font, net.minecraft.world.item.ItemStack stack, int x, int y) {
        graphics.renderItem(stack, x, y);
        graphics.renderItemDecorations(font, stack, x, y);
    }

    /** Ванильная подсказка предмета (название и описание с сервера) поверх всего на этом кадре. */
    public void itemTooltip(Font font, net.minecraft.world.item.ItemStack stack, int x, int y) {
        graphics.setTooltipForNextFrame(font, stack, x, y);
    }
}
