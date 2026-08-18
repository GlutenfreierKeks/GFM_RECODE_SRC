package de.glutenfreierkeks.gfm_recode.client.modules.combat;

public final class InteractionLock {

    private static boolean  acquiredThisWindow         = false;
    private static Runnable pendingAction              = null;

    private InteractionLock() {}

    public static void onMovementPacketSent() {
        if (pendingAction != null) {
            pendingAction.run();
            pendingAction = null;
        }
        acquiredThisWindow = false;
    }

    public static boolean schedule(Runnable action) {
        if (acquiredThisWindow) return false;
        
        acquiredThisWindow = true;
        pendingAction      = action;
        return true;
    }

    public static boolean tryAcquire() {
        if (acquiredThisWindow) return false;
        acquiredThisWindow = true;
        return true;
    }

    public static void reset() {
        acquiredThisWindow         = false;
        pendingAction              = null;
    }
}