package app.sovereign.phone;

import java.util.ArrayDeque;

/** Policy core. Adapters must enforce each action before reporting success.
 * Not wired to the client yet; it makes no device-wide firewall claim.
 */
public final class DefenseMatrix {
    public enum Level { NOMINAL, GUARD, FLOOD, CONTAINMENT, QUARANTINE }
    public enum Signal { REQUEST_SPIKE, UNKNOWN_HEADER, FLOOD, INTRUSION, MEMORY_TAMPER, INTEGRITY_CHECK_FAILED }
    public interface Enforcement {
        void applyGuard() throws Exception;
        void applyFloodLimits() throws Exception;
        void severBridgeAndContain() throws Exception;
        void closeExternalConnections() throws Exception;
        /** Must authenticate the snapshot and check rollback freshness, not just compare an adjacent hash. */
        void restoreAuthenticatedSnapshot() throws Exception;
        void verifyState() throws Exception;
        /** Must execute a Keystore CryptoObject operation authorized by fresh biometric authentication.
         * A caller-provided username or boolean is not proof. */
        void verifyCreatorBiometricAndRevalidate() throws Exception;
    }
    public record Transition(long timeNanos, Level level, Signal cause, boolean enforced) {}
    private final Enforcement enforcement;
    private final ArrayDeque<Transition> audit = new ArrayDeque<>();
    private Level level = Level.NOMINAL;
    private boolean enforced;
    private long lastCheck;
    private boolean checked;
    public DefenseMatrix(Enforcement enforcement) {
        if (enforcement == null) throw new IllegalArgumentException("Enforcement adapter required");
        this.enforcement = enforcement;
    }
    public synchronized Level level() { return level; }
    public synchronized boolean enforcementConfirmed() { return enforced; }
    public synchronized boolean dispatchAllowed() {
        return enforced && level.ordinal() < Level.CONTAINMENT.ordinal();
    }
    /** Use monotonic System.nanoTime(), never wall-clock time. Call before dispatch too.
     * A foreground service/scheduler is still needed for periodic checks while idle. */
    public synchronized void tick(long nowNanos) {
        if (!checked || nowNanos - lastCheck >= 60_000_000_000L) {
            lastCheck = nowNanos; checked = true;
            try {
                enforcement.verifyState();
                if (level == Level.NOMINAL) enforced = true;
            } catch (Exception error) { signal(Signal.INTEGRITY_CHECK_FAILED, nowNanos); }
        }
    }
    public synchronized void signal(Signal signal, long nowNanos) {
        Level requested = switch (signal) {
            case REQUEST_SPIKE, UNKNOWN_HEADER -> Level.GUARD;
            case FLOOD -> Level.FLOOD;
            case INTRUSION -> Level.CONTAINMENT;
            case MEMORY_TAMPER, INTEGRITY_CHECK_FAILED -> Level.QUARANTINE;
        };
        if (requested.ordinal() < level.ordinal()) return;
        level = requested;
        enforced = false; // Fail closed while enforcement is pending or fails.
        try {
            switch (level) {
                case GUARD -> enforcement.applyGuard();
                case FLOOD -> { enforcement.applyGuard(); enforcement.applyFloodLimits(); }
                case CONTAINMENT -> enforcement.severBridgeAndContain();
                case QUARANTINE -> {
                    enforcement.closeExternalConnections();
                    enforcement.restoreAuthenticatedSnapshot();
                }
                default -> throw new IllegalStateException("Unexpected escalation");
            }
            enforced = true;
        } catch (Exception failure) { /* Requested level remains latched; dispatch stays blocked. */ }
        if (audit.size() == 128) audit.removeFirst();
        audit.addLast(new Transition(nowNanos, level, signal, enforced));
    }
    /** No automatic downgrades. Failed authentication leaves the level latched. */
    public synchronized void creatorRecovery() throws Exception {
        enforced = false;
        enforcement.verifyCreatorBiometricAndRevalidate();
        enforcement.verifyState();
        level = Level.NOMINAL;
        enforced = true;
        checked = false; // Force a fresh check before next dispatch.
    }
    public synchronized Transition[] audit() { return audit.toArray(new Transition[0]); }
}
