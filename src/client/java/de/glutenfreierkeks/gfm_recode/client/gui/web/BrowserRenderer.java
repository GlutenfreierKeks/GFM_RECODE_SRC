package de.glutenfreierkeks.gfm_recode.client.gui.web;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.cinemamod.mcef.MCEFSettings;
import de.glutenfreierkeks.gfm_recode.client.Gfm_recodeClient;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.cef.CefApp;
import org.cef.CefSettings;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandlerAdapter;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public final class BrowserRenderer implements AutoCloseable, ResolutionManager.Listener {
    private static final ExecutorService BROWSER_EXECUTOR = Executors.newSingleThreadExecutor(new BrowserThreadFactory());
    private static final Object BACKEND_LOCK = new Object();
    private static volatile boolean backendInitialized;

    private final ResolutionManager resolutionManager;
    private final String debugName;
    private final String initialUrl;
    private final boolean transparent;
    private final Identifier textureIdentifier;

    private volatile ResolutionManager.ResolutionSnapshot resolution;
    private volatile boolean closed;
    private volatile boolean ready;
    private MCEFBrowser browser;
    private boolean textureRegistered;
    private int currentTextureId;
    private double zoomLevel = 0.0;

    public BrowserRenderer(String debugName, String initialUrl, ResolutionManager resolutionManager, boolean transparent) {
        this.debugName = debugName;
        this.initialUrl = initialUrl;
        this.resolutionManager = resolutionManager;
        this.transparent = transparent;
        this.resolution = resolutionManager.getCurrent();
        this.textureIdentifier = Identifier.of("mcef", debugName.toLowerCase());
        resolutionManager.addListener(this);
        BROWSER_EXECUTOR.execute(this::initializeBrowser);
    }



    public static void initializeBackendAsync() { BROWSER_EXECUTOR.execute(BrowserRenderer::ensureBackendInitialized); }
    public static CefApp getInitializedApp() { return MCEF.isInitialized() ? MCEF.getApp().getHandle() : null; }

    private static boolean ensureBackendInitialized() {
        try {
            int attempts = 0;
            while (!MCEF.isInitialized() && attempts < 100) { Thread.sleep(100); attempts++; }
            if (!MCEF.isInitialized()) return false;
            if (!backendInitialized) {
                synchronized (BACKEND_LOCK) {
                    if (!backendInitialized) {
                        MCEFSettings s = MCEF.getSettings();
                        s.setNativeCefLogSeverity(CefSettings.LogSeverity.LOGSEVERITY_DISABLE);
                        s.setBrowserPreloadEnabled(false);
                        backendInitialized = true;
                    }
                }
            }
            return true;
        } catch (Exception e) { return false; }
    }

    private void initializeBrowser() {

        if (closed || !ensureBackendInitialized()) return;
        try {
            MCEF.getClient().addLoadHandler(new CefLoadHandlerAdapter() {
                @Override
                public void onLoadEnd(CefBrowser b, CefFrame f, int s) {
                    if (browser == b && f != null && f.isMain()) ready = true;
                }
            });
            browser = MCEF.createBrowser(initialUrl, transparent, Math.max(1, resolution.framebufferWidth()), Math.max(1, resolution.framebufferHeight()));
            browser.setFocus(true);
        } catch (Exception e) { Gfm_recodeClient.LOG.error("[{}] Failed to create browser", debugName, e); }
    }

    @Override
    public void onResolutionChanged(ResolutionManager.ResolutionSnapshot p, ResolutionManager.ResolutionSnapshot c) {
        this.resolution = c;
        if (browser != null && !closed) {
            BROWSER_EXECUTOR.execute(() -> browser.resize(Math.max(1, c.framebufferWidth()), Math.max(1, c.framebufferHeight())));
            // Texture must be re-registered because GlTexture size is fixed at instantiation
            textureRegistered = false;
        }
    }

    public boolean isReady() { return ready && browser != null; }

    public void renderFullscreen(DrawContext context) {
        if (closed || browser == null || !ready) return;
        int textureId = browser.getRenderer().getTextureID();
        if (textureId == 0) return;

        if (!textureRegistered || currentTextureId != textureId) {
            registerTexture();
            textureRegistered = true;
            currentTextureId = textureId;
        }

        ResolutionManager.ResolutionSnapshot s = resolution;

        // Use drawTexture with explicit GUI_TEXTURED pipeline for 1.21.1 stability
        context.drawTexture(RenderPipelines.GUI_TEXTURED, textureIdentifier,
            0, 0,
            0.0f, 0.0f,
            s.scaledWidth(), s.scaledHeight(),
            s.scaledWidth(), s.scaledHeight()
        );
    }

    private void registerTexture() {
        try {
            int texId = browser.getRenderer().getTextureID();
            if (texId == 0) return;

            // Using Mixin Invokers to create instances of protected classes
            // usage 4 = TEXTURE_BINDING, levels=1, layers=1
            GlTexture glTexture = de.glutenfreierkeks.gfm_recode.mixin.client.GlTextureInvoker.create(
                4, "mcef", TextureFormat.RGBA8,
                resolution.framebufferWidth(), resolution.framebufferHeight(), 1, 1, texId);

            net.minecraft.client.texture.GlTextureView glView = de.glutenfreierkeks.gfm_recode.mixin.client.GlTextureViewInvoker.create(
                glTexture, 0, 1);

            AbstractTexture tex = new AbstractTexture() {
                @Override public void close() {}
            };

            // Using Mixin Accessor to set protected fields
            // The accessor uses the base GpuTexture/View types to match AbstractTexture fields
            de.glutenfreierkeks.gfm_recode.mixin.client.AbstractTextureAccessor accessor = (de.glutenfreierkeks.gfm_recode.mixin.client.AbstractTextureAccessor) tex;
            accessor.setGlTexture(glTexture);
            accessor.setGlTextureView(glView);
            accessor.setSampler(RenderSystem.getSamplerCache().get(FilterMode.LINEAR));

            MinecraftClient.getInstance().getTextureManager().registerTexture(textureIdentifier, tex);
        } catch (Exception e) {
            Gfm_recodeClient.LOG.error("Failed to register MCEF texture", e);
        }
    }

    public void sendMouseMove(int x, int y) { if (browser != null && !closed) browser.sendMouseMove(x, y); }
    public void sendMousePress(int x, int y, int b) { if (browser != null && !closed) { browser.sendMousePress(x, y, b); browser.setFocus(true); } }
    public void sendMouseRelease(int x, int y, int b) { if (browser != null && !closed) { browser.sendMouseRelease(x, y, b); browser.setFocus(true); } }
    public void sendMouseWheel(int x, int y, double v, double h) { if (browser != null && !closed) browser.sendMouseWheel(x, y, v, currentGlfwModifiers()); }
    public void sendKeyPress(int k, int mod) { if (browser != null && !closed) { browser.sendKeyPress(k, GLFW.glfwGetKeyScancode(k), currentGlfwModifiers() | mod); browser.setFocus(true); } }
    public void sendKeyRelease(int k, int mod) { if (browser != null && !closed) { browser.sendKeyRelease(k, GLFW.glfwGetKeyScancode(k), currentGlfwModifiers() | mod); browser.setFocus(true); } }
    public void sendKeyTyped(char c, int mod) { if (browser != null && !closed && c != 0) { browser.sendKeyTyped(c, currentGlfwModifiers() | mod); browser.setFocus(true); } }

    public void executeJavaScript(String script) {
        if (closed || browser == null) return;
        BROWSER_EXECUTOR.execute(() -> { if (browser != null && !closed) browser.executeJavaScript(script, browser.getURL(), 0); });
    }

    public void setZoomLevel(double level) {
        this.zoomLevel = level;
        if (browser != null && !closed) {
            browser.setZoomLevel(level);
        }
    }

    public double getZoomLevel() { return zoomLevel; }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        resolutionManager.removeListener(this);
        BROWSER_EXECUTOR.execute(() -> { if (browser != null) { try { browser.close(); } catch (Exception ignored) {} browser = null; } });
    }

    private static int currentGlfwModifiers() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return 0;
        long h = client.getWindow().getHandle();
        int m = 0;
        if (GLFW.glfwGetKey(h, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(h, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS) m |= GLFW.GLFW_MOD_SHIFT;
        if (GLFW.glfwGetKey(h, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(h, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS) m |= GLFW.GLFW_MOD_CONTROL;
        if (GLFW.glfwGetKey(h, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(h, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS) m |= GLFW.GLFW_MOD_ALT;
        return m;
    }

    private static final class BrowserThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable r) { Thread t = new Thread(r, "gfm-browser-renderer"); t.setDaemon(true); return t; }
    }
}
