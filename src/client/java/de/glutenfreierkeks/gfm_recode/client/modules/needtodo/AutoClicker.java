package de.glutenfreierkeks.gfm_recode.client.modules.needtodo;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.EnumSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import net.minecraft.client.render.Camera;
import net.minecraft.util.Hand;
import org.joml.Matrix4f;

import java.util.Random;

public class AutoClicker extends Module {

    private final Random random = new Random();

    // --- SETTINGS ---
    private final EnumSetting<ClickMode> mode = register(new EnumSetting<>("Mode", "Click mode", ClickMode.BOTH));
    private final BoolSetting onlyWhileHolding = register(new BoolSetting("OnlyWhileHolding", "Only click while holding button", false));
    private final IntSliderSetting cps = register(new IntSliderSetting("CPS", "Clicks per second", 12, 1, 25));
    private final BoolSetting randomize = register(new BoolSetting("Randomize", "Randomize click speed", true));
    private final IntSliderSetting randomVariation = register(new IntSliderSetting("RandomVariation", "Random speed variation", 2, 0, 5));
    private final BoolSetting holdButton = register(new BoolSetting("HoldButton", "Simulate holding button", false));

    // --- STATE ---
    private long lastLeftClick = 0L;
    private long lastRightClick = 0L;
    private long nextLeftClickDelay = 0L;
    private long nextRightClickDelay = 0L;

    public AutoClicker() {
        super("AutoClicker", "Automatically clicks left/right mouse button", Category.MISC);
    }

    @Override
    public void onEnable() {
        lastLeftClick = System.currentTimeMillis();
        lastRightClick = System.currentTimeMillis();
        calculateNextDelay();
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.currentScreen != null) return;

        long now = System.currentTimeMillis();

        // Left Click
        if (shouldClickLeft()) {
            if (canClick(true, now)) {
                performLeftClick();
                lastLeftClick = now;
                calculateNextDelay();
            }
        }

        // Right Click
        if (shouldClickRight()) {
            if (canClick(false, now)) {
                performRightClick();
                lastRightClick = now;
                calculateNextDelay();
            }
        }
    }

    private boolean shouldClickLeft() {
        if (mode.getValue() == ClickMode.RIGHT) return false;

        if (onlyWhileHolding.getValue()) {
            return mc.options.attackKey.isPressed();
        }

        return true;
    }

    private boolean shouldClickRight() {
        if (mode.getValue() == ClickMode.LEFT) return false;

        if (onlyWhileHolding.getValue()) {
            return mc.options.useKey.isPressed();
        }

        return true;
    }

    private boolean canClick(boolean isLeft, long now) {
        long lastClick = isLeft ? lastLeftClick : lastRightClick;
        long nextDelay = isLeft ? nextLeftClickDelay : nextRightClickDelay;

        return (now - lastClick) >= nextDelay;
    }

    private void performLeftClick() {
        if (mc.interactionManager == null || mc.player == null) return;

        if (holdButton.getValue()) {
            // Simulate holding button (continuous mining)
            if (mc.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult bhr) {
                mc.interactionManager.attackBlock(bhr.getBlockPos(), bhr.getSide());
            }
        } else {
            // Normal click
            if (mc.targetedEntity != null) {
                mc.interactionManager.attackEntity(mc.player, mc.targetedEntity);
                mc.player.swingHand(Hand.MAIN_HAND);
            } else {
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private void performRightClick() {
        if (mc.interactionManager == null) return;

        if (holdButton.getValue()) {
            // Simulate holding button (continuous use)
            mc.options.useKey.setPressed(true);

            // Execute right click action
            if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
                mc.interactionManager.interactBlock(
                        mc.player,
                        Hand.MAIN_HAND,
                        (net.minecraft.util.hit.BlockHitResult) mc.crosshairTarget
                );
            } else if (mc.targetedEntity != null) {
                mc.interactionManager.interactEntity(mc.player, mc.targetedEntity, Hand.MAIN_HAND);
            } else {
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            }

            // Release after short delay
            new Thread(() -> {
                try {
                    Thread.sleep(50);
                    mc.execute(() -> mc.options.useKey.setPressed(false));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        } else {
            // Normal click
            if (mc.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult bhr) {
                mc.interactionManager.interactBlock(
                        mc.player,
                        Hand.MAIN_HAND,
                        bhr
                );
            } else if (mc.targetedEntity != null) {
                mc.interactionManager.interactEntity(mc.player, mc.targetedEntity, Hand.MAIN_HAND);
            } else {
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            }
        }
    }

    private void calculateNextDelay() {
        int baseCPS = cps.getValue();
        int baseDelay = 1000 / baseCPS;

        if (randomize.getValue()) {
            int variation = randomVariation.getValue();
            int randomOffset = random.nextInt(variation * 2 + 1) - variation;

            nextLeftClickDelay = Math.max(1, baseDelay + randomOffset * 10);
            nextRightClickDelay = Math.max(1, baseDelay + randomOffset * 10);
        } else {
            nextLeftClickDelay = baseDelay;
            nextRightClickDelay = baseDelay;
        }
    }

    @Override
    public void onDisable() {
        // Make sure to release keys when disabling
        if (mc.options != null) {
            mc.options.attackKey.setPressed(false);
            mc.options.useKey.setPressed(false);
        }
    }

    public String getDisplayInfo() {
        int actualCPS = (int) (1000.0 / Math.max(1, nextLeftClickDelay));
        return mode.getValue().name() + " | " + actualCPS + " CPS";
    }

    public enum ClickMode {
        LEFT,
        RIGHT,
        BOTH
    }
}
