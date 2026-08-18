package de.glutenfreierkeks.gfm_recode.client.modules.combat;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import net.minecraft.client.render.Camera;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class AutoPearl extends Module {

    private int originalSlot = -1;
    private int pearlSlot = -1;
    private int tickCount = 0;
    private boolean throwing = false;

    public AutoPearl() {
        super("AutoPearl", "Throws ender pearl from hotbar and disables", Category.PLAYER);
        this.macroAllowed = false;
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
    }

    @Override
    public void onEnable() {
        if (mc.player == null || mc.world == null) {
            disable();
            return;
        }
        
        // Save original slot
        originalSlot = mc.player.getInventory().getSelectedSlot();
        
        // Find pearl in hotbar
        pearlSlot = -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.ENDER_PEARL) {
                pearlSlot = i;
                break;
            }
        }
        
        // No pearl found
        if (pearlSlot == -1) {
            disable();
            return;
        }
        
        // Start throw sequence
        throwing = true;
        tickCount = 0;
        
        // Switch to pearl
        mc.player.getInventory().setSelectedSlot(pearlSlot);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null) return;
        
        // Find pearl in hotbar
        int pearlSlot = -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.ENDER_PEARL) {
                pearlSlot = i;
                break;
            }
        }
        
        if (pearlSlot == -1) {
            disable();
            return;
        }

        int originalSlot = mc.player.getInventory().getSelectedSlot();
        
        // 1. Swap to pearl
        mc.player.getInventory().setSelectedSlot(pearlSlot);
        
        // 2. Throw pearl (using interactItem for throwing items)
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.swingHand(Hand.MAIN_HAND);
        
        // 3. Swap back immediately
        mc.player.getInventory().setSelectedSlot(originalSlot);
        
        disable();
    }

    @Override
    public void onDisable() {
        // Restore slot if needed
        if (originalSlot != -1 && mc.player != null && mc.player.getInventory().getSelectedSlot() == pearlSlot) {
            mc.player.getInventory().setSelectedSlot(originalSlot);
        }
        throwing = false;
        tickCount = 0;
        originalSlot = -1;
        pearlSlot = -1;
    }
}
