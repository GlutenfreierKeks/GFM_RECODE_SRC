package de.glutenfreierkeks.gfm_recode.client.modules.render;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.ColorSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.KeybindSetting;
import de.glutenfreierkeks.gfm_recode.client.utils.RenderUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public class Debugger extends Module {

    public final KeybindSetting markFirst = register(new KeybindSetting("Mark First", "Mark the block you are looking at as block A", GLFW.GLFW_KEY_Z));
    public final KeybindSetting markSecond = register(new KeybindSetting("Mark Second", "Mark the block you are looking at as block B", GLFW.GLFW_KEY_X));
    public final KeybindSetting clearMarks = register(new KeybindSetting("Clear Marks", "Clear both marked blocks", GLFW.GLFW_KEY_C));

    public final BoolSetting tracers = register(new BoolSetting("Tracers", "Draw tracer lines to both marked blocks", true));
    public final BoolSetting autoCompare = register(new BoolSetting("Auto Compare", "Continuously compare both marked blocks", true));
    public final BoolSetting chatSpam = register(new BoolSetting("Chat Output", "Print every detected change and difference in chat", true));

    public final ColorSetting colorA = register(new ColorSetting("Color A", "Render color for first marked block", 0, 180, 255, 255));
    public final ColorSetting colorB = register(new ColorSetting("Color B", "Render color for second marked block", 255, 170, 0, 255));

    private record BlockSnapshot(BlockPos pos, String blockId, Map<String, String> facts) {}

    private BlockPos firstPos;
    private BlockPos secondPos;
    private BlockSnapshot firstSnapshot;
    private BlockSnapshot secondSnapshot;
    private Map<String, String> lastDifferences = new LinkedHashMap<>();

    private boolean firstHeld;
    private boolean secondHeld;
    private boolean clearHeld;
    private int tickCounter;

    public Debugger() {
        super("Debugger",
                "Marks two blocks, watches every detectable change and reports all differences between them.",
                Category.RENDER);
        this.macroAllowed = true;
    }

    @Override
    protected void onEnable() {
        tickCounter = 0;
        firstHeld = false;
        secondHeld = false;
        clearHeld = false;
        announce("§bDebugger aktiv. Schau auf einen Block und drücke die Keybinds für A/B.");
    }

    @Override
    protected void onDisable() {
        firstSnapshot = null;
        secondSnapshot = null;
        lastDifferences = new LinkedHashMap<>();
        firstHeld = false;
        secondHeld = false;
        clearHeld = false;
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;

        handleSelectionKeys();

        if (tickCounter++ % 5 != 0) return;

        BlockSnapshot newFirst = firstPos != null ? captureSnapshot(firstPos) : null;
        BlockSnapshot newSecond = secondPos != null ? captureSnapshot(secondPos) : null;

        reportSnapshotChanges("A", firstSnapshot, newFirst);
        reportSnapshotChanges("B", secondSnapshot, newSecond);

        firstSnapshot = newFirst;
        secondSnapshot = newSecond;

        if (autoCompare.getValue() && newFirst != null && newSecond != null) {
            reportCrossDifferences(newFirst, newSecond);
        }
    }

    private void handleSelectionKeys() {
        long window = mc.getWindow().getHandle();

        boolean firstPressed = markFirst.isPressed(window);
        if (firstPressed && !firstHeld) {
            BlockPos target = getLookedAtBlock();
            if (target != null) {
                firstPos = target.toImmutable();
                firstSnapshot = captureSnapshot(firstPos);
                announce("§bDebugger §7A gesetzt auf §f" + formatPos(firstPos));
                dumpSnapshot("A", firstSnapshot);
                if (firstSnapshot != null && secondSnapshot != null) {
                    reportCrossDifferences(firstSnapshot, secondSnapshot);
                }
            } else {
                announce("§cDebugger §7Kein Block anvisiert für A.");
            }
        }
        firstHeld = firstPressed;

        boolean secondPressed = markSecond.isPressed(window);
        if (secondPressed && !secondHeld) {
            BlockPos target = getLookedAtBlock();
            if (target != null) {
                secondPos = target.toImmutable();
                secondSnapshot = captureSnapshot(secondPos);
                announce("§6Debugger §7B gesetzt auf §f" + formatPos(secondPos));
                dumpSnapshot("B", secondSnapshot);
                if (firstSnapshot != null && secondSnapshot != null) {
                    reportCrossDifferences(firstSnapshot, secondSnapshot);
                }
            } else {
                announce("§cDebugger §7Kein Block anvisiert für B.");
            }
        }
        secondHeld = secondPressed;

        boolean clearPressed = clearMarks.isPressed(window);
        if (clearPressed && !clearHeld) {
            firstPos = null;
            secondPos = null;
            firstSnapshot = null;
            secondSnapshot = null;
            lastDifferences = new LinkedHashMap<>();
            announce("§7Debugger §fMarkierungen gelöscht.");
        }
        clearHeld = clearPressed;
    }

    private BlockPos getLookedAtBlock() {
        HitResult hit = mc.crosshairTarget;
        if (!(hit instanceof BlockHitResult bhr)) return null;
        return bhr.getBlockPos();
    }

    private BlockSnapshot captureSnapshot(BlockPos pos) {
        if (mc.world == null) return null;

        BlockState state = mc.world.getBlockState(pos);
        Map<String, String> facts = new LinkedHashMap<>();
        String blockId = Registries.BLOCK.getId(state.getBlock()).toString();

        facts.put("block_id", blockId);
        facts.put("block_name", state.getBlock().getName().getString());
        facts.put("translation_key", state.getBlock().getTranslationKey());
        facts.put("state_string", state.toString());
        facts.put("has_block_entity", Boolean.toString(state.hasBlockEntity()));
        facts.put("is_air", Boolean.toString(state.isAir()));
        facts.put("emits_redstone_power", safeBool(() -> state.emitsRedstonePower()));
        facts.put("has_comparator_output", safeBool(() -> state.hasComparatorOutput()));
        facts.put("luminance_declared", safeInt(() -> state.getLuminance()));
        facts.put("light_block_actual", safeInt(() -> mc.world.getLightLevel(net.minecraft.world.LightType.BLOCK, pos)));
        facts.put("light_sky_actual", safeInt(() -> mc.world.getLightLevel(net.minecraft.world.LightType.SKY, pos)));
        facts.put("hardness", safeFloat(() -> state.getHardness(mc.world, pos)));
        facts.put("blast_resistance", safeFloat(() -> state.getBlock().getBlastResistance()));
        facts.put("replaceable", safeBool(() -> state.isReplaceable()));
        facts.put("opaque", safeBool(() -> state.isOpaque()));
        facts.put("solid_block", safeBool(() -> state.isSolidBlock(mc.world, pos)));
        facts.put("has_sided_transparency", safeBool(() -> state.hasSidedTransparency()));
        facts.put("fluid_state", safeString(() -> state.getFluidState().toString()));
        facts.put("collision_shape_empty", safeBool(() -> state.getCollisionShape(mc.world, pos).isEmpty()));
        facts.put("outline_shape_empty", safeBool(() -> state.getOutlineShape(mc.world, pos).isEmpty()));
        facts.put("collision_shape_bounds", safeString(() -> state.getCollisionShape(mc.world, pos).getBoundingBox().toString()));
        facts.put("outline_shape_bounds", safeString(() -> state.getOutlineShape(mc.world, pos).getBoundingBox().toString()));
        facts.put("is_redstone_named", Boolean.toString(blockId.toLowerCase(Locale.ROOT).contains("redstone")));
        facts.put("property_count", Integer.toString(state.getProperties().size()));

        int maxReceived = 0;
        int maxEmitted = 0;
        int maxComparator = 0;
        int maxWeak = 0;
        int maxStrong = 0;
        int poweredSides = 0;
        int redstoneLikeNeighbors = 0;

        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.offset(direction);
            BlockState neighbor = mc.world.getBlockState(neighborPos);

            int received = safeIntValue(() -> mc.world.getReceivedRedstonePower(neighborPos));
            if (received > 0 || safeBoolValue(() -> mc.world.isReceivingRedstonePower(neighborPos))) {
                poweredSides++;
            }
            maxReceived = Math.max(maxReceived, safeIntValue(() -> mc.world.getReceivedRedstonePower(pos.offset(direction))));
            maxEmitted = Math.max(maxEmitted, safeIntValue(() -> mc.world.getEmittedRedstonePower(pos, direction)));
            maxComparator = Math.max(maxComparator, safeIntValue(() -> state.getComparatorOutput(mc.world, pos, direction)));
            maxWeak = Math.max(maxWeak, safeIntValue(() -> state.getWeakRedstonePower(mc.world, pos, direction)));
            maxStrong = Math.max(maxStrong, safeIntValue(() -> state.getStrongRedstonePower(mc.world, pos, direction)));

            String neighborId = Registries.BLOCK.getId(neighbor.getBlock()).toString();
            if (neighborId.contains("redstone")
                    || neighbor.contains(net.minecraft.state.property.Properties.POWER)
                    || neighbor.contains(net.minecraft.state.property.Properties.POWERED)
                    || safeBoolValue(() -> neighbor.emitsRedstonePower())) {
                redstoneLikeNeighbors++;
            }

            String dirName = direction.asString();
            facts.put("dir:" + dirName + ":emitted", Integer.toString(safeIntValue(() -> mc.world.getEmittedRedstonePower(pos, direction))));
            facts.put("dir:" + dirName + ":weak", Integer.toString(safeIntValue(() -> state.getWeakRedstonePower(mc.world, pos, direction))));
            facts.put("dir:" + dirName + ":strong", Integer.toString(safeIntValue(() -> state.getStrongRedstonePower(mc.world, pos, direction))));
            facts.put("dir:" + dirName + ":comparator", Integer.toString(safeIntValue(() -> state.getComparatorOutput(mc.world, pos, direction))));
            facts.put("dir:" + dirName + ":side_solid", safeBool(() -> state.isSideSolidFullSquare(mc.world, pos, direction)));
            facts.put("dir:" + dirName + ":neighbor_block", neighborId);
            facts.put("dir:" + dirName + ":neighbor_state", safeString(neighbor::toString));
        }

        facts.put("received_redstone_power", Integer.toString(safeIntValue(() -> mc.world.getReceivedRedstonePower(pos))));
        facts.put("is_receiving_redstone_power", safeBool(() -> mc.world.isReceivingRedstonePower(pos)));
        facts.put("emitted_redstone_power_max", Integer.toString(maxEmitted));
        facts.put("neighbor_received_power_max", Integer.toString(maxReceived));
        facts.put("comparator_output_max", Integer.toString(maxComparator));
        facts.put("weak_redstone_power_max", Integer.toString(maxWeak));
        facts.put("strong_redstone_power_max", Integer.toString(maxStrong));
        facts.put("powered_neighbor_sides", Integer.toString(poweredSides));
        facts.put("redstone_like_neighbors", Integer.toString(redstoneLikeNeighbors));

        facts.put("has_property_power", Boolean.toString(state.contains(net.minecraft.state.property.Properties.POWER)));
        facts.put("has_property_powered", Boolean.toString(state.contains(net.minecraft.state.property.Properties.POWERED)));
        facts.put("has_property_lit", Boolean.toString(state.contains(net.minecraft.state.property.Properties.LIT)));
        facts.put("has_property_open", Boolean.toString(state.contains(net.minecraft.state.property.Properties.OPEN)));
        facts.put("has_property_triggered", Boolean.toString(state.contains(net.minecraft.state.property.Properties.TRIGGERED)));
        facts.put("has_property_attached", Boolean.toString(state.contains(net.minecraft.state.property.Properties.ATTACHED)));
        facts.put("has_property_inverted", Boolean.toString(state.contains(net.minecraft.state.property.Properties.INVERTED)));
        facts.put("has_property_delay", Boolean.toString(state.contains(net.minecraft.state.property.Properties.DELAY)));
        facts.put("has_property_locked", Boolean.toString(state.contains(net.minecraft.state.property.Properties.LOCKED)));
        facts.put("has_property_enabled", Boolean.toString(state.contains(net.minecraft.state.property.Properties.ENABLED)));
        facts.put("has_property_waterlogged", Boolean.toString(state.contains(net.minecraft.state.property.Properties.WATERLOGGED)));
        facts.put("has_property_facing", Boolean.toString(state.contains(net.minecraft.state.property.Properties.FACING)));
        facts.put("has_property_horizontal_facing", Boolean.toString(state.contains(net.minecraft.state.property.Properties.HORIZONTAL_FACING)));
        facts.put("has_property_axis", Boolean.toString(state.contains(net.minecraft.state.property.Properties.AXIS)));

        for (Property<?> property : state.getProperties()) {
            facts.put("property:" + property.getName(), readProperty(state, property));
        }

        BlockEntity blockEntity = state.hasBlockEntity() ? mc.world.getBlockEntity(pos) : null;
        if (blockEntity != null) {
            facts.put("block_entity_class", blockEntity.getClass().getSimpleName());
            NbtCompound nbt = safeNbt(blockEntity);
            facts.put("block_entity_nbt", nbt == null ? "<null>" : nbt.toString());
        } else {
            facts.put("block_entity_class", "<none>");
            facts.put("block_entity_nbt", "<none>");
        }

        return new BlockSnapshot(pos.toImmutable(), blockId, facts);
    }

    private void reportSnapshotChanges(String label, BlockSnapshot oldSnapshot, BlockSnapshot newSnapshot) {
        if (!chatSpam.getValue()) return;
        if (oldSnapshot == null || newSnapshot == null) return;

        Set<String> keys = new TreeSet<>();
        keys.addAll(oldSnapshot.facts().keySet());
        keys.addAll(newSnapshot.facts().keySet());

        for (String key : keys) {
            String oldValue = oldSnapshot.facts().get(key);
            String newValue = newSnapshot.facts().get(key);
            if (Objects.equals(oldValue, newValue)) continue;

            announce("§7[Debugger " + label + "] §f" + key + " §7: §c" + shorten(oldValue) + " §8-> §a" + shorten(newValue));
        }
    }

    private void reportCrossDifferences(BlockSnapshot first, BlockSnapshot second) {
        Map<String, String> currentDifferences = new LinkedHashMap<>();
        Set<String> keys = new TreeSet<>();
        keys.addAll(first.facts().keySet());
        keys.addAll(second.facts().keySet());

        for (String key : keys) {
            String firstValue = first.facts().get(key);
            String secondValue = second.facts().get(key);
            if (Objects.equals(firstValue, secondValue)) continue;
            currentDifferences.put(key, "A=" + shorten(firstValue) + " | B=" + shorten(secondValue));
        }

        if (!chatSpam.getValue()) {
            lastDifferences = currentDifferences;
            return;
        }

        Set<String> allDiffKeys = new LinkedHashSet<>();
        allDiffKeys.addAll(lastDifferences.keySet());
        allDiffKeys.addAll(currentDifferences.keySet());

        for (String key : allDiffKeys) {
            String oldValue = lastDifferences.get(key);
            String newValue = currentDifferences.get(key);
            if (Objects.equals(oldValue, newValue)) continue;

            if (newValue == null) {
                announce("§7[Debugger Diff] §aUnterschied weg: §f" + key);
            } else if (oldValue == null) {
                announce("§7[Debugger Diff] §eUnterschied: §f" + key + " §8=> §7" + newValue);
            } else {
                announce("§7[Debugger Diff] §6Geändert: §f" + key + " §8=> §7" + newValue);
            }
        }

        lastDifferences = currentDifferences;
    }

    private void dumpSnapshot(String label, BlockSnapshot snapshot) {
        if (!chatSpam.getValue() || snapshot == null) return;
        announce("§7[Debugger " + label + "] §fBlock: §e" + snapshot.blockId() + " §8@ §7" + formatPos(snapshot.pos()));
        for (Map.Entry<String, String> entry : snapshot.facts().entrySet()) {
            announce("§8- §f" + entry.getKey() + " §7= §b" + shorten(entry.getValue()));
        }
    }

    private String readProperty(BlockState state, Property<?> property) {
        try {
            return String.valueOf(state.get(property));
        } catch (Exception e) {
            return "<error>";
        }
    }

    private NbtCompound safeNbt(BlockEntity blockEntity) {
        if (mc.world == null) return null;
        try {
            return blockEntity.createNbt(mc.world.getRegistryManager());
        } catch (Exception e) {
            return null;
        }
    }

    private String safeBool(UnsafeBooleanSupplier supplier) {
        try {
            return Boolean.toString(supplier.getAsBoolean());
        } catch (Exception e) {
            return "<error>";
        }
    }

    private boolean safeBoolValue(UnsafeBooleanSupplier supplier) {
        try {
            return supplier.getAsBoolean();
        } catch (Exception e) {
            return false;
        }
    }

    private String safeInt(UnsafeIntSupplier supplier) {
        try {
            return Integer.toString(supplier.getAsInt());
        } catch (Exception e) {
            return "<error>";
        }
    }

    private int safeIntValue(UnsafeIntSupplier supplier) {
        try {
            return supplier.getAsInt();
        } catch (Exception e) {
            return 0;
        }
    }

    private String safeFloat(UnsafeFloatSupplier supplier) {
        try {
            return String.format(java.util.Locale.ROOT, "%.2f", supplier.getAsFloat());
        } catch (Exception e) {
            return "<error>";
        }
    }

    private String safeString(UnsafeStringSupplier supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            return "<error>";
        }
    }

    private String formatPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private String shorten(String text) {
        if (text == null) return "<null>";
        return text.length() > 180 ? text.substring(0, 177) + "..." : text;
    }

    private void announce(String message) {
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal(message), false);
        }
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
        Vec3d camPos = camera.getCameraPos();
        boolean doTracers = tracers.getValue();

        if (firstPos != null) {
            renderMarkedBlock(posMatrix, camPos, firstPos, new Color(colorA.getArgb(), true), doTracers);
        }
        if (secondPos != null) {
            renderMarkedBlock(posMatrix, camPos, secondPos, new Color(colorB.getArgb(), true), doTracers);
        }
    }

    private void renderMarkedBlock(Matrix4f posMatrix, Vec3d camPos, BlockPos pos, Color color, boolean doTracers) {
        double dx = pos.getX() - camPos.x;
        double dy = pos.getY() - camPos.y;
        double dz = pos.getZ() - camPos.z;

        Box box = new Box(dx, dy, dz, dx + 1, dy + 1, dz + 1);
        Color fill = new Color(color.getRed(), color.getGreen(), color.getBlue(), 35);
        RenderUtil.drawBox(posMatrix, box, color, 1.4);
        RenderUtil.drawFilledBox(posMatrix, box, fill);

        if (doTracers) {
            RenderUtil.drawTracer(posMatrix, new Vec3d(0, -0.1, 0), new Vec3d(dx + 0.5, dy + 0.5, dz + 0.5), color);
        }
    }

    @Override
    public String getDisplayInfo() {
        int count = (firstPos != null ? 1 : 0) + (secondPos != null ? 1 : 0);
        return count == 0 ? null : count + "/2";
    }

    @FunctionalInterface
    private interface UnsafeBooleanSupplier {
        boolean getAsBoolean() throws Exception;
    }

    @FunctionalInterface
    private interface UnsafeIntSupplier {
        int getAsInt() throws Exception;
    }

    @FunctionalInterface
    private interface UnsafeFloatSupplier {
        float getAsFloat() throws Exception;
    }

    @FunctionalInterface
    private interface UnsafeStringSupplier {
        String get() throws Exception;
    }
}
