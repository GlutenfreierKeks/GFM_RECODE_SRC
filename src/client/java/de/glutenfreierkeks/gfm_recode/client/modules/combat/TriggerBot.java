package de.glutenfreierkeks.gfm_recode.client.modules.combat;

import de.glutenfreierkeks.gfm_recode.client.Gfm_recodeClient;
import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.DoubleSliderSetting;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.concurrent.ThreadLocalRandom;

public class TriggerBot extends Module {

    private final DoubleSliderSetting hitChance  = new DoubleSliderSetting("HitChance",  "Trefferchance (%)",                  100.0,  0.0, 100.0);
    private final DoubleSliderSetting minDelayMs = new DoubleSliderSetting("Min Delay",  "Minimale Angriffsverzögerung (ms)",   80.0,  1.0, 500.0);
    private final DoubleSliderSetting maxDelayMs = new DoubleSliderSetting("Max Delay",  "Maximale Angriffsverzögerung (ms)",  140.0,  1.0, 500.0);
    private final BoolSetting         preferCrit = new BoolSetting("Prefer Crit",  "Wartet bis du auf dem Boden bist oder fällst um einen Crit zu landen", false);

    private long lastAttackTime  = 0;
    private long nextAttackDelay = 0;

    public TriggerBot() {
        super("TriggerBot", "Greift automatisch an wenn das Fadenkreuz auf einem Spieler ist", Category.PLAYER);
        this.macroAllowed = false;
        register(hitChance);
        register(minDelayMs);
        register(maxDelayMs);
        register(preferCrit);
        this.macroAllowed = false;
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {}

    @Override
    public void onEnable() {
        lastAttackTime  = 0;
        nextAttackDelay = randomLong(minDelayMs.getValue(), maxDelayMs.getValue());
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null || mc.world == null) return;

        long now = System.currentTimeMillis();
        if (now - lastAttackTime < nextAttackDelay) return;

        float requiredCooldown = mc.player.isOnGround() ? 1.0f : 0.9f;
        if (mc.player.getAttackCooldownProgress(0.5f) < requiredCooldown) return;
        
        if (preferCrit.getValue()) {
            if (!mc.player.isOnGround() && mc.player.fallDistance <= 0.0f && !mc.player.isClimbing() && !mc.player.isTouchingWater() && !mc.player.hasVehicle()) return;
        }

        HitResult hit = mc.crosshairTarget;
        if (!(hit instanceof EntityHitResult ehr)) return;

        Entity target = ehr.getEntity();
        if (!(target instanceof PlayerEntity) || target == mc.player || de.glutenfreierkeks.gfm_recode.client.utils.FriendManager.isFriend(target.getName().getString())) return;

        // ── Server-seitiger Hitbox-Check ─────────────────────────────────
        Vec3d  eyes     = mc.player.getEyePos();
        Vec3d  look     = mc.player.getRotationVec(1.0f);
        double reach    = mc.player.getEntityInteractionRange();
        Vec3d  end      = eyes.add(look.multiply(reach));
        Box    expanded = target.getBoundingBox().expand(0.05);
        if (expanded.raycast(eyes, end).isEmpty()) return;

        // ── Hit-Chance Roll ───────────────────────────────────────────────
        if (ThreadLocalRandom.current().nextDouble(100.0) >= hitChance.getValue()) {
            lastAttackTime  = now;
            nextAttackDelay = randomLong(minDelayMs.getValue(), maxDelayMs.getValue());
            return;
        }

        // ── MaceShieldBreaker check (density mace, 20 CPS / every tick) ─────────────────
        var maceShieldBreaker = Gfm_recodeClient.modules.getByName("MaceShieldBreaker");
        boolean maceShieldBreakerActive = maceShieldBreaker != null && maceShieldBreaker.isEnabled();
        boolean targetShielding = (target instanceof PlayerEntity pe) && pe.isBlocking();

        if (maceShieldBreakerActive && targetShielding && (mc.player.fallDistance > 10.0f || target.fallDistance > 10.0f)) {
            int densityMaceSlot = -1;
            try {
                var enchantRegistry = mc.world.getRegistryManager().getOrThrow(net.minecraft.registry.RegistryKeys.ENCHANTMENT);
                var enchantKey = net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.ENCHANTMENT, net.minecraft.util.Identifier.of("minecraft:density"));
                var enchantEntry = enchantRegistry.getEntry(enchantKey.getValue());
                for (int i = 0; i < 9; i++) {
                    var stack = mc.player.getInventory().getStack(i);
                    if (stack.getItem() == net.minecraft.item.Items.MACE) {
                        if (enchantEntry.isPresent()) {
                            int level = net.minecraft.enchantment.EnchantmentHelper.getLevel(enchantEntry.get(), stack);
                            if (level > 0) {
                                densityMaceSlot = i;
                                break;
                            }
                        }
                        if (densityMaceSlot == -1) {
                            densityMaceSlot = i;
                        }
                    }
                }
            } catch (Exception ignored) {}

            if (densityMaceSlot != -1) {
                mc.player.getInventory().setSelectedSlot(densityMaceSlot);
                mc.interactionManager.attackEntity(mc.player, target);
                mc.player.swingHand(Hand.MAIN_HAND);
                lastAttackTime = now;
                nextAttackDelay = 50; // 20 times per second = 50ms (or next tick)
                return;
            }
        }

        // ── ShieldBreaker (disabler) + MaceSwap double hit check ──────────
        var shieldBreaker = Gfm_recodeClient.modules.getByName("ShieldBreaker");
        boolean shieldBreakerActive = shieldBreaker != null && shieldBreaker.isEnabled();
        MaceSwap maceSwap = (MaceSwap) Gfm_recodeClient.modules.getByName("MaceSwap");
        boolean maceSwapActive = maceSwap != null && maceSwap.isEnabled();

        if (shieldBreakerActive && maceSwapActive && targetShielding) {
            int axeSlot = -1;
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getStack(i).getItem() instanceof net.minecraft.item.AxeItem) {
                    axeSlot = i;
                    break;
                }
            }
            if (axeSlot != -1) {
                int maceSlot = maceSwap.findMace();
                if (maceSlot != -1) {
                    int originalSlot = mc.player.getInventory().getSelectedSlot();
                    // 1. Swap to axe
                    mc.player.getInventory().setSelectedSlot(axeSlot);
                    // 2. Attack once (disable shield)
                    mc.interactionManager.attackEntity(mc.player, target);
                    mc.player.swingHand(Hand.MAIN_HAND);
                    // 3. Swap to mace immediately
                    mc.player.getInventory().setSelectedSlot(maceSlot);
                    // 4. Attack with mace
                    mc.interactionManager.attackEntity(mc.player, target);
                    mc.player.swingHand(Hand.MAIN_HAND);

                    // Set MaceSwap ticks to cooldown back to original slot
                    maceSwap.originalSlot = originalSlot;
                    maceSwap.ticksLeft = maceSwap.getCooldownTicks();

                    lastAttackTime = now;
                    nextAttackDelay = randomLong(minDelayMs.getValue(), maxDelayMs.getValue());
                    return;
                }
            }
        }

        // ── MaceSwap aktiv? → Flag setzen, sonst selbst schlagen ─────────
        if (maceSwapActive) {
            if (maceSwap.onAttack(target)) {
                WTap wTap = Gfm_recodeClient.modules.getModuleByClass(WTap.class);
                if (wTap != null && wTap.isEnabled()) {
                    wTap.onAttack((PlayerEntity) target);
                }
                lastAttackTime  = now;
                nextAttackDelay = randomLong(minDelayMs.getValue(), maxDelayMs.getValue());
                return;
            }
        }
        
        WTap wTap = Gfm_recodeClient.modules.getModuleByClass(WTap.class);
        if (wTap != null && wTap.isEnabled()) {
            wTap.onAttack((PlayerEntity) target);
        }
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);

        lastAttackTime  = now;
        nextAttackDelay = randomLong(minDelayMs.getValue(), maxDelayMs.getValue());
    }

    @Override
    public void onDisable() {
        lastAttackTime  = 0;
        nextAttackDelay = 0;
    }

    private long randomLong(double min, double max) {
        if (min >= max) return (long) min;
        return (long) ThreadLocalRandom.current().nextDouble(min, max);
    }
}
