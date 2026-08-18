package de.glutenfreierkeks.gfm_recode.client.modules.render;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.joml.Matrix4f;

import java.util.*;

public class AncientFinder extends Module {

    public final IntSliderSetting scanRadius =
            register(new IntSliderSetting("Scan Radius", "Radius to scan for Ancient Debris", 16, 4, 32));

    // ── Interner Snapshot ─────────────────────────────────────────────────────

    private static class DebrisSnapshot {
        final BlockPos    pos;
        final BlockState  state;
        final NbtCompound nbt;
        final List<String> entityTypes;

        DebrisSnapshot(BlockPos pos, BlockState state, NbtCompound nbt, List<String> entityTypes) {
            this.pos         = pos;
            this.state       = state;
            this.nbt         = nbt;
            this.entityTypes = entityTypes;
        }
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private BlockPos       lastLookedPos      = null;
    private DebrisSnapshot lastLookedSnapshot = null;
    private int            tickCounter        = 0;

    public AncientFinder() {
        super("AncientFinder", "Schaut auf Ancient Debris → vergleicht NBT/State/Entities mit Umgebung", Category.RENDER);
        this.macroAllowed = false;
    }

    @Override
    protected void onEnable() {
        lastLookedPos      = null;
        lastLookedSnapshot = null;
        tickCounter        = 0;
    }

    @Override
    protected void onDisable() {
        lastLookedPos      = null;
        lastLookedSnapshot = null;
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;

        HitResult hit = mc.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            lastLookedPos      = null;
            lastLookedSnapshot = null;
            return;
        }

        BlockPos lookedAt = ((BlockHitResult) hit).getBlockPos();

        if (mc.world.getBlockState(lookedAt).getBlock() != Blocks.ANCIENT_DEBRIS) {
            lastLookedPos      = null;
            lastLookedSnapshot = null;
            return;
        }

        boolean newBlock = !lookedAt.equals(lastLookedPos);
        if (!newBlock && tickCounter++ % 20 != 0) return;

        if (newBlock) {
            tickCounter   = 0;
            lastLookedPos = lookedAt;
        }

        lastLookedSnapshot = buildSnapshot(lookedAt);
        scanAndCompare();
    }

    // ── Scan & Vergleich ──────────────────────────────────────────────────────

    private void scanAndCompare() {
        if (lastLookedSnapshot == null || mc.world == null || mc.player == null) return;

        BlockPos center = mc.player.getBlockPos();
        int      radius = scanRadius.getValue();

        List<String> differences = new ArrayList<>();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = center.add(x, y, z);
                    if (pos.equals(lastLookedPos)) continue;
                    if (mc.world.getBlockState(pos).getBlock() != Blocks.ANCIENT_DEBRIS) continue;

                    DebrisSnapshot other = buildSnapshot(pos);
                    List<String>   diffs = compare(lastLookedSnapshot, other);

                    if (!diffs.isEmpty()) {
                        differences.add("§e" + pos.toShortString() + "§r: " + String.join(", ", diffs));
                    }
                }
            }
        }

        if (differences.isEmpty()) {
            sendChat("§a[AncientFinder] Alle Debris in Reichweite identisch mit §e"
                    + lastLookedPos.toShortString());
        } else {
            sendChat("§6[AncientFinder] §rVergleich mit §e" + lastLookedPos.toShortString() + "§r:");
            for (String diff : differences) {
                sendChat("  §7→ " + diff);
            }
        }
    }

    // ── Snapshot bauen ────────────────────────────────────────────────────────

    private DebrisSnapshot buildSnapshot(BlockPos pos) {
        BlockState   state       = mc.world.getBlockState(pos);
        NbtCompound  nbt         = getBlockNbt(pos);
        List<String> entityTypes = getEntitiesAt(pos);
        return new DebrisSnapshot(pos, state, nbt, entityTypes);
    }

    // ── Detaillierter Vergleich ───────────────────────────────────────────────

    private List<String> compare(DebrisSnapshot ref, DebrisSnapshot other) {
        List<String> diffs = new ArrayList<>();

        // 1. Block-State Properties
        String stateRef   = extractProperties(ref.state);
        String stateOther = extractProperties(other.state);
        if (!stateRef.equals(stateOther)) {
            diffs.add("§bState§r: ref=" + stateRef + " vs " + stateOther);
        }

        // 2. NBT Vergleich
        if (ref.nbt == null && other.nbt != null) {
            diffs.add("§dNBT§r: ref hat keins, other=" + other.nbt);
        } else if (ref.nbt != null && other.nbt == null) {
            diffs.add("§dNBT§r: ref=" + ref.nbt + ", other hat keins");
        } else if (ref.nbt != null && other.nbt != null && !ref.nbt.equals(other.nbt)) {
            Set<String> allKeys = new LinkedHashSet<>();
            allKeys.addAll(ref.nbt.getKeys());
            allKeys.addAll(other.nbt.getKeys());

            for (String key : allKeys) {
                boolean inRef   = ref.nbt.contains(key);
                boolean inOther = other.nbt.contains(key);

                if (inRef && !inOther) {
                    diffs.add("§dNBT§r: -" + key + " (nur ref)");
                } else if (!inRef && inOther) {
                    diffs.add("§dNBT§r: +" + key + "=" + other.nbt.get(key) + " (nur other)");
                } else if (!ref.nbt.get(key).equals(other.nbt.get(key))) {
                    diffs.add("§dNBT§r: " + key + " ref=" + ref.nbt.get(key)
                            + " vs " + other.nbt.get(key));
                }
            }
        }

        // 3. Entities Vergleich
        List<String> refE   = new ArrayList<>(ref.entityTypes);
        List<String> otherE = new ArrayList<>(other.entityTypes);
        Collections.sort(refE);
        Collections.sort(otherE);

        if (!refE.equals(otherE)) {
            List<String> onlyOther = new ArrayList<>(otherE);
            onlyOther.removeAll(refE);
            List<String> onlyRef = new ArrayList<>(refE);
            onlyRef.removeAll(otherE);

            if (!onlyOther.isEmpty())
                diffs.add("§cEntities§r: +" + onlyOther + " (nur other)");
            if (!onlyRef.isEmpty())
                diffs.add("§cEntities§r: -" + onlyRef + " (nur ref)");
        }

        return diffs;
    }

    // ── Hilfsmethoden ─────────────────────────────────────────────────────────

    private String extractProperties(BlockState state) {
        String s      = state.toString();
        int    bracket = s.indexOf('[');
        return bracket >= 0 ? s.substring(bracket) : "[]";
    }

    private List<String> getEntitiesAt(BlockPos pos) {
        if (mc.world == null) return Collections.emptyList();
        Box box = new Box(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
        List<String> types = new ArrayList<>();
        for (Entity entity : mc.world.getEntitiesByClass(Entity.class, box, e -> true)) {
            types.add(entity.getClass().getSimpleName());
        }
        return types;
    }

    private NbtCompound getBlockNbt(BlockPos pos) {
        if (mc.world == null) return null;
        BlockEntity be = mc.world.getBlockEntity(pos);
        if (be == null) return null;
        try {
            return be.createNbt(mc.world.getRegistryManager());
        } catch (Exception e) {
            return null;
        }
    }

    private void sendChat(String message) {
        if (mc.player == null) return;
        mc.player.sendMessage(Text.literal(message), false);
    }

    // ── HUD ───────────────────────────────────────────────────────────────────

    @Override
    public String getDisplayInfo() {
        if (lastLookedPos == null) return "kein Debris";
        return lastLookedPos.toShortString();
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {}
}