package de.glutenfreierkeks.gfm_recode.client.modules.combat;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.DoubleSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.EnumSetting;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.ThreadLocalRandom;

public class WTap extends Module {

    private final EnumSetting<EventType> eventType = new EnumSetting<>("Event", "When the WTap should trigger", EventType.Attack);
    private final DoubleSliderSetting releaseMs = new DoubleSliderSetting("Release W ms", "How long sprint stays fully off", 55.0, 5.0, 300.0);
    private final DoubleSliderSetting actionMs = new DoubleSliderSetting("WTap nach ms", "Delay before releasing sprint", 0.0, 0.0, 200.0);
    private final DoubleSliderSetting hitPer = new DoubleSliderSetting("Jeden X Hits", "Trigger every X hits", 1.0, 1.0, 10.0);
    private final DoubleSliderSetting range = new DoubleSliderSetting("Range", "Max distance to the target", 3.0, 1.0, 6.0);
    private final DoubleSliderSetting chance = new DoubleSliderSetting("Chance %", "Trigger chance", 100.0, 0.0, 100.0);
    private final BoolSetting onlyPlayers = new BoolSetting("OnlyPlayers", "Only trigger on players", true);
    private final BoolSetting onlySword = new BoolSetting("OnlySword", "Only trigger with weapons", false);
    private final BoolSetting dynamic = new BoolSetting("DynamicTime", "Longer release when fighting close", false);
    private final DoubleSliderSetting tapMultiplier = new DoubleSliderSetting("DynMultiplier", "How much distance affects release time", 1.0, 0.0, 5.0);

    private WTapState state = WTapState.NONE;
    private long timerStart = 0L;
    private long timerTarget = 0L;
    private int hits = 0;
    private int hitsNeeded = 1;
    private PlayerEntity target;
    private int sprintSuppressTicks = 0;

    public enum EventType {
        Attack,
        Hurt
    }

    public enum WTapState {
        NONE,
        WAITING_TO_RELEASE,
        RELEASING
    }

    public WTap() {
        super("WTap", "Fully drops sprint and W after a hit for stronger reset timing", Category.PLAYER);
        register(eventType);
        register(onlyPlayers);
        register(onlySword);
        register(releaseMs);
        register(actionMs);
        register(hitPer);
        register(chance);
        register(range);
        register(dynamic);
        register(tapMultiplier);
        this.macroAllowed = false;
    }

    public void onAttack(PlayerEntity attacked) {
        if (eventType.getValue() != EventType.Attack) {
            return;
        }
        this.target = attacked;
        tryWTap();
    }

    public void onHurt(PlayerEntity hurt) {
        if (eventType.getValue() != EventType.Hurt || hurt != this.target) {
            return;
        }
        tryWTap();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (sprintSuppressTicks > 0) {
            sprintSuppressTicks--;
        }
        if (state == WTapState.WAITING_TO_RELEASE && now - timerStart >= timerTarget) {
            startRelease();
        } else if (state == WTapState.RELEASING) {
            forceSprintDrop();
            if (now - timerStart >= timerTarget) {
                finishRelease();
            }
        }
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
    }

    private void tryWTap() {
        if (state != WTapState.NONE || target == null || mc.player == null) {
            return;
        }

        if (onlyPlayers.getValue() && !(target instanceof PlayerEntity)) {
            return;
        }

        if (onlySword.getValue() && !isHoldingWeapon()) {
            return;
        }

        if (distanceTo(target) > range.getValue()) {
            return;
        }

        hits++;
        if (hits < Math.max(1, (int) Math.round(hitPer.getValue()))) {
            return;
        }

        if (Math.random() > chance.getValue() / 100.0) {
            resetHitGoal();
            return;
        }

        if (actionMs.getValue() <= 0.5) {
            startRelease();
            return;
        }

        state = WTapState.WAITING_TO_RELEASE;
        timerStart = System.currentTimeMillis();
        timerTarget = (long) ThreadLocalRandom.current().nextDouble(
                Math.max(0.0, actionMs.getValue() - 1.0),
                actionMs.getValue() + 1.0
        );
    }

    private void startRelease() {
        state = WTapState.RELEASING;
        forceSprintDrop();

        double duration = ThreadLocalRandom.current().nextDouble(
                Math.max(1.0, releaseMs.getValue() - 2.0),
                releaseMs.getValue() + 2.0
        );

        if (dynamic.getValue() && target != null) {
            double dist = distanceTo(target);
            if (dist < 3.0) {
                duration += (3.0 - dist) * tapMultiplier.getValue() * 10.0;
            }
        }

        timerStart = System.currentTimeMillis();
        timerTarget = (long) duration;
    }

    private void finishRelease() {
        if (isPhysicalForwardPressed()) {
            mc.options.forwardKey.setPressed(true);
        }
        mc.options.sprintKey.setPressed(false);
        sprintSuppressTicks = 3;

        state = WTapState.NONE;
        resetHitGoal();
    }

    private void forceSprintDrop() {
        mc.options.forwardKey.setPressed(false);
        mc.options.sprintKey.setPressed(false);
        mc.player.setSprinting(false);
    }

    private void resetHitGoal() {
        hits = 0;
        hitsNeeded = Math.max(1, (int) Math.round(hitPer.getValue()));
    }

    private double distanceTo(PlayerEntity other) {
        Vec3d a = mc.player.getEntityPos();
        Vec3d b = other.getEntityPos();
        return a.distanceTo(b);
    }

    private boolean isHoldingWeapon() {
        var stack = mc.player.getMainHandStack();
        return !stack.isEmpty() && stack.getMaxDamage() > 0;
    }

    private boolean isPhysicalForwardPressed() {
        return isPhysicalPressed(mc.options.forwardKey);
    }

    private boolean isPhysicalSprintPressed() {
        return isPhysicalPressed(mc.options.sprintKey);
    }

    private boolean isPhysicalPressed(KeyBinding keyBinding) {
        try {
            var field = KeyBinding.class.getDeclaredField("boundKey");
            field.setAccessible(true);
            InputUtil.Key bound = (InputUtil.Key) field.get(keyBinding);
            long handle = mc.getWindow().getHandle();
            return switch (bound.getCategory()) {
                case KEYSYM -> GLFW.glfwGetKey(handle, bound.getCode()) == GLFW.GLFW_PRESS;
                case MOUSE -> GLFW.glfwGetMouseButton(handle, bound.getCode()) == GLFW.GLFW_PRESS;
                default -> false;
            };
        } catch (ReflectiveOperationException ignored) {
            return keyBinding.isPressed();
        }
    }

    private void resetState() {
        state = WTapState.NONE;
        target = null;
        sprintSuppressTicks = 0;
        resetHitGoal();
        if (mc.player != null) {
            if (isPhysicalForwardPressed()) {
                mc.options.forwardKey.setPressed(true);
            }
            if (isPhysicalSprintPressed()) {
                mc.options.sprintKey.setPressed(true);
            }
        }
    }

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        resetState();
    }

    public boolean isSuppressingSprint() {
        return state != WTapState.NONE || sprintSuppressTicks > 0;
    }
}
