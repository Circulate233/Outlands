package com.cleanroommc.common;

import net.minecraftforge.fml.relauncher.Side;

// TODO: temp
public final class CleanroomEnvironment {

    private static Side side;
    private static final boolean deobfuscatedEnvironment;

    // TODO: check obf class name?
    static {
        boolean deobfEnvironment = System.getProperty("net.minecraftforge.gradle.GradleStart.srg.srg-mcp") != null;
        deobfEnvironment |= getDevExtraPath() != null;

        deobfuscatedEnvironment = deobfEnvironment;
    }

    /** The property a dev launcher (Unimined) uses to hand extra classpath entries to the loader. */
    public static final String DEV_EXTRAPATH = "outlands.dev.extrapath";

    /** What the property was called before the rename. Still honoured so an older Unimined keeps working. */
    private static final String LEGACY_DEV_EXTRAPATH = "crl.dev.extrapath";

    public static String getDevExtraPath() {
        String path = System.getProperty(DEV_EXTRAPATH);
        return path != null ? path : System.getProperty(LEGACY_DEV_EXTRAPATH);
    }

    public static void setSide(Side side) {
        if (CleanroomEnvironment.side != null) {
            return;
        }
        CleanroomEnvironment.side = side;
    }

    public static boolean isDev() {
        return deobfuscatedEnvironment;
    }

    public static Side side() {
        if (side == null) {
            throw new IllegalStateException("Side not yet set.");
        }
        return side;
    }

    private CleanroomEnvironment() { }

}
