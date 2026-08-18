package de.glutenfreierkeks.gfm_recode.client.modules.combat;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.DoubleSliderSetting;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class CrystalPlace extends Module {

    private final DoubleSliderSetting cps        = new DoubleSliderSetting("Crystals/sec", "Wie viele Kristalle pro Sekunde", 20.0, 1.0, 20.0);
    private final DoubleSliderSetting placeDelay = new DoubleSliderSetting("Place Delay",  "Wait between placements (ms)",     0.0, 0.0, 250.0, 1);

    private long    lastActionTime = 0;
    private long    lastPlaceTime  = 0;
    private long    lastBreakTime  = 0;
    // Abwechselnd: true = dieser Tick brechen, false = dieser Tick platzieren
    private boolean breakPhase     = true;

    public CrystalPlace() {
        super("CrystalPlace", "Platziert/bricht End Crystal den du anschaust (Crystal in Hand)", Category.PLAYER);
        register(cps);
        register(placeDelay);
        this.macroAllowed = false;
    }

    @Override public void render3D(Matrix4f p, Matrix4f pr, Camera c, float td) {}

    @Override
    public void onEnable() {
        lastActionTime = 0;
        lastPlaceTime  = 0;
        lastBreakTime  = 0;
        breakPhase     = true;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (HitCrystal.isRunning) return;
        if (mc.player.getMainHandStack().getItem() != Items.END_CRYSTAL) return;
        if (!mc.options.useKey.isPressed()) return;

        long now           = System.currentTimeMillis();
        long delayRequired = (long) (1000.0 / cps.getValue());
        if (now - lastActionTime < delayRequired) return;

        Vec3d eyePos = mc.player.getEyePos();

        // ── Ziel-Block ermitteln ──────────────────────────────────────────
        BlockPos base = null;

        if (mc.crosshairTarget instanceof BlockHitResult bhr) {
            var block = mc.world.getBlockState(bhr.getBlockPos()).getBlock();
            if (block == Blocks.OBSIDIAN || block == Blocks.BEDROCK) {
                if (eyePos.distanceTo(Vec3d.of(bhr.getBlockPos()).add(0.5, 1.0, 0.5)) <= 4.5) {
                    base = bhr.getBlockPos();
                }
            }
        }

        // ── Crystal direkt angeschaut → immer brechen (eigener Pfad) ────
        if (mc.crosshairTarget instanceof EntityHitResult ehr
                && ehr.getEntity() instanceof EndCrystalEntity crystal) {
            if (eyePos.distanceTo(crystal.getEntityPos()) <= 4.5) {
                mc.interactionManager.attackEntity(mc.player, crystal);
                mc.player.swingHand(Hand.MAIN_HAND);
                lastActionTime = now;
                lastBreakTime  = now;
                breakPhase     = false; // nächster Tick → Place
                return;
            }
        }

        if (base == null) return;

        // ── Abwechselnd Break / Place ─────────────────────────────────────
        EndCrystalEntity nearby = findCrystalAbove(base);

        if (breakPhase) {
            // Nur brechen wenn Crystal existiert
            if (nearby != null) {
                mc.interactionManager.attackEntity(mc.player, nearby);
                mc.player.swingHand(Hand.MAIN_HAND);
                lastActionTime = now;
                lastBreakTime  = now;
            }
            breakPhase = false; // nächster Tick → Place

        } else {
            // Nur platzieren
            long pd = (long) placeDelay.getValue().doubleValue();
            if (pd > 0 && now - lastPlaceTime < pd) {
                breakPhase = true;
                return;
            }

            BlockHitResult placeHit = getRealBlockHit(base);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeHit);
            mc.player.swingHand(Hand.MAIN_HAND);
            lastActionTime = now;
            lastPlaceTime  = now;
            breakPhase     = true; // nächster Tick → Break
        }
    }

    private BlockHitResult getRealBlockHit(BlockPos targetBlock) {
        double x = 0.3 + ThreadLocalRandom.current().nextDouble(0.4);
        double z = 0.3 + ThreadLocalRandom.current().nextDouble(0.4);
        double y = 0.99 + ThreadLocalRandom.current().nextDouble(0.01);
        return new BlockHitResult(Vec3d.of(targetBlock).add(x, y, z), Direction.UP, targetBlock, false);
    }

    private EndCrystalEntity findCrystalAbove(BlockPos base) {
        if (mc.world == null) return null;
        Box box = new Box(base).expand(0.1, 2.5, 0.1);
        List<EndCrystalEntity> list = mc.world.getEntitiesByClass(EndCrystalEntity.class, box, e -> true);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override public void onDisable() { lastActionTime = 0; }
}