package de.glutenfreierkeks.gfm_recode.client.modules.needtodo;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix4f;
import net.minecraft.client.render.Camera;

public class AutoTool extends Module {

    private int savedSlot = -1;

    public AutoTool() {
        super("AutoTool", "Wechselt automatisch zum besten Werkzeug beim Abbauen eines Blocks.", Category.PLAYER);
    }

    @Override
    public void onEnable() {
        savedSlot = -1;
    }

    @Override
    public void onDisable() {
        restoreSlot();
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {

    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        // Nur aktiv wenn der Spieler gerade abbaut (Attack-Key gedrückt)
        boolean isMining = mc.options.attackKey.isPressed();

        if (!isMining) {
            restoreSlot();
            return;
        }

        // Schaut der Spieler auf einen Block?
        if (mc.crosshairTarget == null || mc.crosshairTarget.getType() != HitResult.Type.BLOCK) {
            restoreSlot();
            return;
        }

        BlockPos   pos   = ((BlockHitResult) mc.crosshairTarget).getBlockPos();
        BlockState state = mc.world.getBlockState(pos);

        if (state.isAir()) {
            restoreSlot();
            return;
        }

        int bestSlot    = getBestToolSlot(state);
        if (bestSlot < 0) return;

        int currentSlot = mc.player.getInventory().getSelectedSlot();

        // Slot merken falls noch nicht gemacht
        if (savedSlot < 0) savedSlot = currentSlot;

        if (currentSlot != bestSlot) {
            mc.player.getInventory().setSelectedSlot(bestSlot);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private int getBestToolSlot(BlockState state) {
        int   bestSlot  = -1;
        float bestSpeed = -1f;

        for (int s = 0; s < 9; s++) {
            ItemStack stack = mc.player.getInventory().getStack(s);
            float speed = stack.getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot  = s;
            }
        }
        return bestSlot;
    }

    private void restoreSlot() {
        if (savedSlot >= 0 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(savedSlot);
            savedSlot = -1;
        }
    }

    @Override
    public String getDisplayInfo() {
        if (mc.player == null) return "idle";
        if (savedSlot >= 0) return "slot " + (mc.player.getInventory().getSelectedSlot() + 1);
        return "watching";
    }
}
