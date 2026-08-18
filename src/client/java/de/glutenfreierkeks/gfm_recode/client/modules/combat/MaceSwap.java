package de.glutenfreierkeks.gfm_recode.client.modules.combat;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.EnumSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import net.minecraft.client.render.Camera;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

public class MaceSwap extends Module {
    private final IntSliderSetting cooldownTicks = register(new IntSliderSetting("Cooldown", "How many ticks to stay on the mace", 2, 1, 20));

    // TriggerBot can still use this if it wants, but we'll prioritize onAttack
    // TriggerBot can still use this if it wants, but we'll prioritize onAttack
    public static Entity pendingAttackTarget = null;

    public boolean isProgrammaticAttack = false;
    public int originalSlot = -1;
    public int ticksLeft = -1;

    public MaceSwap() {
        super("MaceSwap", "Swaps to mace for every hit then back after a configurable delay", Category.PLAYER);
        this.macroAllowed = false;
    }

    public int getCooldownTicks() {
        return cooldownTicks.getValue();
    }

    /**
     * Intercepts an attack and performs the mace swap sequence.
     * @return true if the original attack should be canceled.
     */
    public boolean onAttack(Entity target) {
        if (mc.player == null || mc.interactionManager == null || target == null) return false;
        if (isProgrammaticAttack || ticksLeft > 0) return false; // Already in a swap sequence

        int maceSlot = findMace();
        if (maceSlot == -1) return false;

        int currentSlot = mc.player.getInventory().getSelectedSlot();
        if (maceSlot == currentSlot) return false; // Already holding it

        // Start sequence
        isProgrammaticAttack = true;
        originalSlot = currentSlot;

        // 1. Swap to Mace
        mc.player.getInventory().setSelectedSlot(maceSlot);

        // 2. Perform Attack
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);

        // 3. Set delay to swap back
        ticksLeft = cooldownTicks.getValue();

        isProgrammaticAttack = false;
        return true; // Cancel vanilla attack
    }

    private final EnumSetting<EnchantmentPreference> preference = register(new EnumSetting<>("Prefer", "Which enchantment to prioritize.", EnchantmentPreference.BREACH));

    public enum EnchantmentPreference {
        BREACH("minecraft:breach"),
        WIND_BURST("minecraft:wind_burst"),
        DENSITY("minecraft:density");

        final String id;
        EnchantmentPreference(String id) { this.id = id; }
    }

    public int findMace() {
        if (mc.player == null) return -1;
        int bestSlot = -1;
        int maxLevel = -1;

        for (int i = 0; i < 9; i++) {
            var stack = mc.player.getInventory().getStack(i);
            // Support both Mace and Trident (Spear)
            if (stack.getItem() == Items.MACE || stack.getItem() == Items.TRIDENT) {
                if (bestSlot == -1) bestSlot = i;

                // For Mace, check enchantments
                if (stack.getItem() == Items.MACE) {
                    RegistryKey<Enchantment> enchantKey = RegistryKey.of(RegistryKeys.ENCHANTMENT, Identifier.of(preference.getValue().id));
                    var enchantRegistry = mc.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
                    var enchantEntry = enchantRegistry.getEntry(enchantKey.getValue());
                    
                    int level = 0;
                    if (enchantEntry.isPresent()) {
                        level = EnchantmentHelper.getLevel(enchantEntry.get(), stack);
                    }

                    if (level > maxLevel) {
                        maxLevel = level;
                        bestSlot = i;
                    }
                }
            }
        }
        return bestSlot;
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {}

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null) return;

        // Handle delay for swapping back
        if (ticksLeft > 0) {
            ticksLeft--;
            if (ticksLeft == 0) {
                if (originalSlot != -1) {
                    mc.player.getInventory().setSelectedSlot(originalSlot);
                }
                originalSlot = -1;
                ticksLeft = -1;
            }
        }

        // Support for TriggerBot's flag
        if (pendingAttackTarget != null) {
            onAttack(pendingAttackTarget);
            pendingAttackTarget = null;
        }
    }

    @Override
    public void onEnable() {
        pendingAttackTarget = null;
        isProgrammaticAttack = false;
        ticksLeft = -1;
        originalSlot = -1;
    }

    @Override
    public void onDisable() {
        if (originalSlot != -1 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(originalSlot);
        }
        pendingAttackTarget = null;
        isProgrammaticAttack = false;
        ticksLeft = -1;
        originalSlot = -1;
    }
}
