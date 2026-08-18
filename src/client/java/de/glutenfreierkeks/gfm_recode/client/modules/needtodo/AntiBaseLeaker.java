package de.glutenfreierkeks.gfm_recode.client.modules.needtodo;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.EnumSetting;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.model.BlockStateModel;
import org.joml.Matrix4f;

import java.util.Set;

public class AntiBaseLeaker extends Module {

    // ── Static singleton für Mixin-Zugriff ────────────────────────────────────

    private static AntiBaseLeaker INSTANCE = null;

    // Neue statische Hilfsmethode damit der Mixin Rekursion verhindern kann
    public static BlockState getDisguiseStateStatic() {
        if (INSTANCE == null || !INSTANCE.isEnabled()) return null;
        return INSTANCE.getDisguiseState();
    }

    public static BlockStateModel getDisguise(
            BlockState state,
            BlockRenderManager manager) {

        if (INSTANCE == null || !INSTANCE.isEnabled()) return null;
        if (!INSTANCE.shouldDisguise(state)) return null;

        BlockState disguiseState = INSTANCE.getDisguiseState();

        // Rekursion verhindern
        if (state.getBlock() == disguiseState.getBlock()) return null;

        return manager.getModel(disguiseState);
    }

    // ── Disguise Texture Enum ─────────────────────────────────────────────────

    public enum DisguiseTexture {
        DEEPSLATE,
        NETHERITE,
        STONE
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    private final EnumSetting<DisguiseTexture> texture = register(
            new EnumSetting<>("Texture", "Disguise texture", DisguiseTexture.DEEPSLATE));

    private final BoolSetting hideOres      = register(new BoolSetting("HideOres",      "Hide ores", true));
    private final BoolSetting hideDeepslate = register(new BoolSetting("HideDeepslate", "Hide deepslate variants", true));
    private final BoolSetting hideTuff      = register(new BoolSetting("HideTuff",       "Hide tuff variants", true));
    private final BoolSetting hideGravel    = register(new BoolSetting("HideGravel",     "Hide gravel", true));
    private final BoolSetting hideClay      = register(new BoolSetting("HideClay",       "Hide clay", true));
    private final BoolSetting hideBedrock   = register(new BoolSetting("HideBedrock",    "Hide bedrock", true));
    private final BoolSetting hideAncient   = register(new BoolSetting("HideAncient",    "Hide ancient debris", true));
    private final BoolSetting hideMud       = register(new BoolSetting("HideMud",        "Hide mud variants", true));

    // ── Block Sets ────────────────────────────────────────────────────────────

    private static final Set<Block> DEEPSLATE_ORES = Set.of(
            Blocks.DEEPSLATE_COAL_ORE,
            Blocks.DEEPSLATE_IRON_ORE,
            Blocks.DEEPSLATE_GOLD_ORE,
            Blocks.DEEPSLATE_COPPER_ORE,
            Blocks.DEEPSLATE_LAPIS_ORE,
            Blocks.DEEPSLATE_REDSTONE_ORE,
            Blocks.DEEPSLATE_DIAMOND_ORE,
            Blocks.DEEPSLATE_EMERALD_ORE
    );

    private static final Set<Block> STONE_ORES = Set.of(
            Blocks.COAL_ORE,
            Blocks.IRON_ORE,
            Blocks.GOLD_ORE,
            Blocks.COPPER_ORE,
            Blocks.LAPIS_ORE,
            Blocks.REDSTONE_ORE,
            Blocks.DIAMOND_ORE,
            Blocks.EMERALD_ORE,
            Blocks.NETHER_QUARTZ_ORE,
            Blocks.NETHER_GOLD_ORE,
            Blocks.GILDED_BLACKSTONE
    );

    private static final Set<Block> DEEPSLATE_VARIANTS = Set.of(
            Blocks.DEEPSLATE,
            Blocks.COBBLED_DEEPSLATE,
            Blocks.POLISHED_DEEPSLATE,
            Blocks.CHISELED_DEEPSLATE,
            Blocks.CRACKED_DEEPSLATE_BRICKS,
            Blocks.CRACKED_DEEPSLATE_TILES,
            Blocks.DEEPSLATE_BRICKS,
            Blocks.DEEPSLATE_TILES,
            Blocks.INFESTED_DEEPSLATE
    );

    private static final Set<Block> TUFF_VARIANTS = Set.of(
            Blocks.TUFF,
            Blocks.POLISHED_TUFF,
            Blocks.CHISELED_TUFF,
            Blocks.TUFF_BRICKS,
            Blocks.CHISELED_TUFF_BRICKS
    );

    private static final Set<Block> MUD_VARIANTS = Set.of(
            Blocks.MUD,
            Blocks.PACKED_MUD,
            Blocks.MUD_BRICKS
    );

    // ── Konstruktor / Lifecycle ───────────────────────────────────────────────

    public AntiBaseLeaker() {
        super("AntiBaseLeaker",
                "Disguises tell-tale blocks to hide base locations in screenshots.",
                Category.MISC);
    }

    @Override
    public void onEnable() {
        INSTANCE = this;
        reloadChunks();
    }

    @Override
    public void onDisable() {
        INSTANCE = null;
        reloadChunks();
    }

    private void reloadChunks() {
        if (mc.worldRenderer != null) {
            mc.worldRenderer.reload();
        }
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
    }

    // ── Core ─────────────────────────────────────────────────────────────────

    public boolean shouldDisguise(BlockState state) {
        Block b = state.getBlock();

        if (hideOres.getValue()) {
            if (DEEPSLATE_ORES.contains(b)) return true;
            if (STONE_ORES.contains(b))     return true;
        }
        if (hideDeepslate.getValue() && DEEPSLATE_VARIANTS.contains(b)) return true;
        if (hideTuff.getValue()      && TUFF_VARIANTS.contains(b))      return true;
        if (hideGravel.getValue()) {
            if (b == Blocks.GRAVEL || b == Blocks.SUSPICIOUS_GRAVEL)    return true;
        }
        if (hideClay.getValue()    && b == Blocks.CLAY)                 return true;
        if (hideBedrock.getValue() && b == Blocks.BEDROCK)              return true;
        if (hideAncient.getValue()) {
            if (b == Blocks.ANCIENT_DEBRIS || b == Blocks.SUSPICIOUS_SAND) return true;
        }
        if (hideMud.getValue() && MUD_VARIANTS.contains(b))             return true;

        return false;
    }

    public BlockState getDisguiseState() {
        return switch (texture.getValue()) {
            case NETHERITE -> Blocks.NETHERITE_BLOCK.getDefaultState();
            case STONE     -> Blocks.STONE.getDefaultState();
            default        -> Blocks.DEEPSLATE.getDefaultState();
        };
    }

    // ── HUD ──────────────────────────────────────────────────────────────────

    public String getDisplayInfo() {
        return switch (texture.getValue()) {
            case NETHERITE -> "netherite";
            case STONE     -> "stone";
            default        -> "deepslate";
        };
    }
}
