package de.glutenfreierkeks.gfm_recode.client.gui.imgui;

import de.glutenfreierkeks.gfm_recode.client.Gfm_recodeClient;
import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.KeybindSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class MenuScreen extends Screen {
    public MenuScreen() {
        super(Text.literal(Gfm_recodeClient.NAME + " Menu"));
        // Mark menu as opened so it renders
        Menu.getInstance().setOpened(true);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        super.removed();
        Menu.getInstance().closeMenu();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x44000000);
        Menu.getInstance().drawMinecraft(context, this.width, this.height, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        if (Menu.getInstance().mouseClicked(click.x(), click.y(), click.button(), this.width, this.height)) {
            return true;
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (Menu.getInstance().mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount, this.width, this.height)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput event) {
        if (event.isEscape() || event.key() == GLFW.GLFW_KEY_K) {
            MinecraftClient.getInstance().setScreen(null);
            return true;
        }

        if (Gfm_recodeClient.modules != null) {
            for (Module module : Gfm_recodeClient.modules.getAll()) {
                for (var setting : module.getSettings()) {
                    if (setting instanceof KeybindSetting ks && ks.isListening()) {
                        ks.setKey(event.key());
                        return true;
                    }
                }
            }
        }

        return super.keyPressed(event);
    }
}
