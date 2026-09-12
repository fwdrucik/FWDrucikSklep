package android.os;

/** JVM-only tracing boundary; the Compose runtime and save/restore registry remain real. */
public final class Trace {
    private Trace() {}
    public static void beginSection(String sectionName) {}
    public static void endSection() {}
}
