package de.glutenfreierkeks.gfm_recode.client.gui.imgui;

import com.mojang.blaze3d.systems.RenderSystem;
import de.glutenfreierkeks.gfm_recode.client.Gfm_recodeClient;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiBackendFlags;
import imgui.flag.ImGuiConfigFlags;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class ImGuiManager {
    private static final ImGuiManager INSTANCE = new ImGuiManager();

    private final ImGuiImplGlfw imGuiGlfw = new ImGuiImplGlfw();
    private final ImGuiImplGl3  imGuiGl3  = new ImGuiImplGl3();

    private long windowHandle = 0;
    private boolean initialized = false;
    private boolean nativesLoaded = false;

    private ImGuiManager() {}

    public static ImGuiManager getInstance() {
        return INSTANCE;
    }

    public void onGlfwInit(long handle) {
        this.windowHandle = handle;
    }

    public void tryRenderThreadInit() {
        if (this.initialized || this.windowHandle == 0) return;
        if (!loadNatives()) return;

        initializeImGui();

        imGuiGlfw.init(this.windowHandle, false);
        imGuiGl3.init();
        imGuiGlfw.installCallbacks(this.windowHandle);

        this.initialized = true;
        Gfm_recodeClient.LOG.info("ImGuiManager initialized (handle={})", this.windowHandle);
    }

    /**
     * Extracts imgui-java64.dll from the JAR into a temp file and loads it via
     * System.load(). This bypasses java.library.path entirely, fixing the crash
     * on launchers like Modrinth that place natives in their own directory.
     */
    private boolean loadNatives() {
        if (nativesLoaded) return true;
        try {
            String libName = "/imgui/imgui-java64.dll";
            InputStream in = ImGuiManager.class.getResourceAsStream(libName);
            if (in == null) {
                // fallback: some versions embed it without the imgui/ prefix
                in = ImGuiManager.class.getResourceAsStream("/imgui-java64.dll");
            }
            if (in == null) {
                return false;
            }
            Path tmp = Files.createTempFile("imgui-java64-", ".dll");
            tmp.toFile().deleteOnExit();
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            in.close();
            System.load(tmp.toAbsolutePath().toString());
            nativesLoaded = true;
            Gfm_recodeClient.LOG.info("imgui-java natives loaded from temp: {}", tmp);
            return true;
        } catch (IOException | UnsatisfiedLinkError e) {
            Gfm_recodeClient.LOG.error("Failed to load imgui-java natives", e);
            return false;
        }
    }

    private void initializeImGui() {
        ImGui.createContext();
        ImGuiIO io = ImGui.getIO();
        io.setIniFilename(null);
        io.setConfigFlags(ImGuiConfigFlags.NavEnableKeyboard);
        io.setBackendFlags(ImGuiBackendFlags.HasMouseCursors | ImGuiBackendFlags.HasSetMousePos);

        ImGui.styleColorsDark();
        var style = ImGui.getStyle();
        style.setWindowRounding(8f);
        style.setWindowPadding(10f, 10f);
        style.setFrameRounding(6f);
        style.setItemSpacing(8f, 8f);
        style.setScrollbarRounding(8f);
        style.setPopupRounding(8f);
    }

    public void onFrameRender() {
        if (!this.initialized) return;
        if (RenderSystem.tryGetDevice() == null) return;

        imGuiGlfw.newFrame();
        imGuiGl3.newFrame();
        ImGui.newFrame();

        float width  = MinecraftClient.getInstance().getWindow().getWidth();
        float height = MinecraftClient.getInstance().getWindow().getHeight();
        float scale  = MathHelper.clamp(height / 1080f, 0.8f, 1.75f);

        Menu.getInstance().draw(width, height, scale);

        ImGui.render();
        endFrame();
    }

    private void endFrame() {
        imGuiGl3.renderDrawData(ImGui.getDrawData());

        if (ImGui.getIO().hasConfigFlags(ImGuiConfigFlags.ViewportsEnable)) {
            long backupWindowPtr = org.lwjgl.glfw.GLFW.glfwGetCurrentContext();
            ImGui.updatePlatformWindows();
            ImGui.renderPlatformWindowsDefault();
            org.lwjgl.glfw.GLFW.glfwMakeContextCurrent(backupWindowPtr);
        }
    }
}