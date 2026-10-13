import app.sovereign.phone.DefenseMatrix;

public final class DefenseMatrixCheck {
    static final class Adapter implements DefenseMatrix.Enforcement {
        boolean fail, denyRecovery;
        int checks;
        public void applyGuard() throws Exception { if (fail) throw new Exception(); }
        public void applyFloodLimits() throws Exception { if (fail) throw new Exception(); }
        public void severBridgeAndContain() {}
        public void closeExternalConnections() {}
        public void restoreAuthenticatedSnapshot() {}
        public void verifyState() { checks++; }
        public void verifyCreatorBiometricAndRevalidate() throws Exception { if (denyRecovery) throw new Exception(); }
    }
    static void check(boolean condition) { if (!condition) throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        Adapter adapter = new Adapter(); DefenseMatrix matrix = new DefenseMatrix(adapter);
        check(!matrix.dispatchAllowed()); matrix.tick(1); check(matrix.dispatchAllowed());
        matrix.tick(59_000_000_000L); check(adapter.checks == 1);
        matrix.tick(60_000_000_001L); check(adapter.checks == 2);
        adapter.fail = true; matrix.signal(DefenseMatrix.Signal.REQUEST_SPIKE, 61_000_000_000L); check(!matrix.dispatchAllowed());
        adapter.fail = false; matrix.signal(DefenseMatrix.Signal.FLOOD, 62_000_000_000L); check(matrix.dispatchAllowed());
        matrix.signal(DefenseMatrix.Signal.INTRUSION, 63_000_000_000L); check(!matrix.dispatchAllowed());
        matrix.signal(DefenseMatrix.Signal.REQUEST_SPIKE, 64_000_000_000L); check(matrix.level() == DefenseMatrix.Level.CONTAINMENT);
        matrix.signal(DefenseMatrix.Signal.MEMORY_TAMPER, 65_000_000_000L); check(!matrix.dispatchAllowed());
        adapter.denyRecovery = true; try { matrix.creatorRecovery(); throw new AssertionError(); } catch (Exception expected) {}
        check(matrix.level() == DefenseMatrix.Level.QUARANTINE && !matrix.dispatchAllowed());
        adapter.denyRecovery = false; matrix.creatorRecovery(); check(matrix.level() == DefenseMatrix.Level.NOMINAL);
        System.out.println("Defense matrix policy checks passed");
    }
}
