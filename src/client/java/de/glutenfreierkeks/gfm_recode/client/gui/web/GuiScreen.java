package de.glutenfreierkeks.gfm_recode.client.gui.web;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class GuiScreen extends Screen {
    private static String getUrl() {
        return "http://localhost:" + WebUiServer.currentPort + "/";
    }

    private final ResolutionManager resolutionManager = ResolutionManager.getInstance();
    private BrowserRenderer browser;

    public GuiScreen() {
        super(Text.literal("GFM Web GUI"));
    }

    @Override
    protected void init() {
        if (browser == null) {
            browser = new BrowserRenderer("gui", getUrl(), resolutionManager, true);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Draw Minecraft background (including the new blur) FIRST
        super.render(context, mouseX, mouseY, delta);

        if (browser != null && browser.isReady()) {
            browser.renderFullscreen(context);
        } else {
            context.drawCenteredTextWithShadow(this.textRenderer, "Loading browser...", this.width / 2, this.height / 2, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        if (browser != null) {
            ResolutionManager.ResolutionSnapshot snapshot = resolutionManager.getCurrent();
            browser.sendMousePress(snapshot.toFramebufferX(click.x()), snapshot.toFramebufferY(click.y()), click.button());
            return true;
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (browser != null) {
            ResolutionManager.ResolutionSnapshot snapshot = resolutionManager.getCurrent();
            browser.sendMouseRelease(snapshot.toFramebufferX(click.x()), snapshot.toFramebufferY(click.y()), click.button());
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (browser != null) {
            ResolutionManager.ResolutionSnapshot snapshot = resolutionManager.getCurrent();
            browser.sendMouseMove(snapshot.toFramebufferX(mouseX), snapshot.toFramebufferY(mouseY));
        }
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (browser != null) {
            ResolutionManager.ResolutionSnapshot snapshot = resolutionManager.getCurrent();
            
            // Allow MCEF to handle native scrolling if Ctrl is not pressed
            if (!(GLFW.glfwGetKey(MinecraftClient.getInstance().getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
                  GLFW.glfwGetKey(MinecraftClient.getInstance().getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS)) {
                browser.sendMouseWheel(
                        snapshot.toFramebufferX(mouseX),
                        snapshot.toFramebufferY(mouseY),
                        verticalAmount,
                        (int) horizontalAmount
                );
            } else {
                // Zooming
                double current = browser.getZoomLevel();
                browser.setZoomLevel(current + (verticalAmount * 0.25));
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.isEscape()) {
            if (de.glutenfreierkeks.gfm_recode.client.Gfm_recodeClient.modules != null && 
                de.glutenfreierkeks.gfm_recode.client.Gfm_recodeClient.modules.isAnyModuleListening()) {
                // Let the browser handle Escape to unbind
                return false;
            }
            close();
            return true;
        }
        if (browser != null) {
            int key = input.key();
            boolean ctrl = (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
            if (ctrl) {
                if (key == GLFW.GLFW_KEY_EQUAL || key == GLFW.GLFW_KEY_KP_ADD) {
                    browser.setZoomLevel(browser.getZoomLevel() + 0.25);
                    return true;
                } else if (key == GLFW.GLFW_KEY_MINUS || key == GLFW.GLFW_KEY_KP_SUBTRACT) {
                    browser.setZoomLevel(browser.getZoomLevel() - 0.25);
                    return true;
                } else if (key == GLFW.GLFW_KEY_0 || key == GLFW.GLFW_KEY_KP_0) {
                    browser.setZoomLevel(0.0);
                    return true;
                }
            }
            browser.sendKeyPress(key, 0);
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean keyReleased(KeyInput input) {
        if (browser != null) {
            browser.sendKeyRelease(input.key(), 0);
            return true;
        }
        return super.keyReleased(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (browser != null && !input.asString().isEmpty()) {
            browser.sendKeyTyped(input.asString().charAt(0), 0);
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public void close() {
        if (this.client != null && this.client.currentScreen != this) {
            return;
        }
        super.close();
    }

    @Override
    public void removed() {
        if (browser != null) {
            browser.close();
            browser = null;
        }
        
        // Force mouse lock to prevent the "2x escape" issue
        if (this.client != null) {
            this.client.mouse.lockCursor();
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
