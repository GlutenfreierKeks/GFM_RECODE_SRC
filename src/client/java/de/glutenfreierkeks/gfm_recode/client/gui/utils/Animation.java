package de.glutenfreierkeks.gfm_recode.client.gui.utils;

/**
 * Lightweight time-based animation helper.
 *
 * Usage:
 *   Animation anim = new Animation(200);   // 200 ms
 *   anim.start();                          // play forward
 *   anim.reverse();                        // play backwards (picks up from current position)
 *   double t = anim.getEaseOut();          // 0.0 → 1.0
 */
public class Animation {

    private final long durationMs;
    private long   startTime  = -1;
    private double startValue = 0.0;   // value at the moment direction changed
    private boolean forward   = true;

    public Animation(long durationMs) {
        this.durationMs = durationMs;
    }

    // ── Control ───────────────────────────────────────────────────────────────

    /** Play forward from the current position. */
    public void start() {
        startValue = getRaw();
        startTime  = System.currentTimeMillis();
        forward    = true;
    }

    /** Play backward from the current position. */
    public void reverse() {
        startValue = getRaw();
        startTime  = System.currentTimeMillis();
        forward    = false;
    }

    /** Convenience: set direction without changing the play state. */
    public void setForward(boolean forward) {
        if (this.forward != forward) {
            if (forward) start(); else reverse();
        }
    }

    /** Jump directly to 0 or 1 without animating. */
    public void setImmediate(boolean atEnd) {
        startValue = atEnd ? 1.0 : 0.0;
        startTime  = -1;
        forward    = atEnd;
    }

    // ── Values ────────────────────────────────────────────────────────────────

    /**
     * Raw linear progress [0, 1].
     */
    public double getRaw() {
        if (startTime < 0) return forward ? 1.0 : 0.0;
        double elapsed = System.currentTimeMillis() - startTime;
        double progress = Math.min(1.0, elapsed / durationMs);
        double value;
        if (forward) {
            value = startValue + progress * (1.0 - startValue);
        } else {
            value = startValue - progress * startValue;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    /** Smooth ease-in-out (cubic Hermite). */
    public double getEaseInOut() {
        double t = getRaw();
        return t * t * (3 - 2 * t);
    }

    /** Ease-out quad — fast start, slow finish. Good for panel slide-in. */
    public double getEaseOut() {
        double t = getRaw();
        return 1 - (1 - t) * (1 - t);
    }

    /** Ease-in quad — slow start, fast finish. Good for fade-out. */
    public double getEaseIn() {
        double t = getRaw();
        return t * t;
    }

    /** Ease-out with a slight overshoot (elastic spring feel). */
    public double getSpring() {
        double t = getRaw();
        // Back ease-out approximation
        double c1 = 1.70158;
        double c3 = c1 + 1;
        return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2);
    }

    /** Returns true when the animation has fully settled (no active transition). */
    public boolean isFinished() {
        if (startTime < 0) return true;
        return System.currentTimeMillis() - startTime >= durationMs;
    }

    /** Convenience: cast to float for interpolation helpers. */
    public float getEaseOutF()   { return (float) getEaseOut(); }
    public float getEaseInOutF() { return (float) getEaseInOut(); }
    public float getRawF()       { return (float) getRaw(); }
}