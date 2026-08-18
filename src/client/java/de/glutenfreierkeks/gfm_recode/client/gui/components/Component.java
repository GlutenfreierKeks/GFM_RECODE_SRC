package de.glutenfreierkeks.gfm_recode.client.gui.components;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;

public abstract class Component {
    protected int x, y, width, height;
    protected boolean visible = true;

    public Component(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public abstract void render(DrawContext ctx, int mx, int my, float delta);

    public void mouseClicked(Click click) {}

    public void mouseDragged(double mx, double my, int button, double ox, double oy) {}

    public void mouseReleased(double mx, double my, int button) {}

    public void keyPressed(KeyInput key) {}

    public void charTyped(CharInput charInput) {}

    public boolean isHovered(int mx, int my) {
        return visible && mx >= x && mx <= x + width && my >= y && my <= y + height;
    }

    public void setPos(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void setSize(int w, int h) {
        this.width = w;
        this.height = h;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }
}
