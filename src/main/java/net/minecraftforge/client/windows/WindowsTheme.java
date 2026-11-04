/// Copyright under MIT https://github.com/LemonCaramel/Mica
package net.minecraftforge.client.windows;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;
import com.sun.jna.ptr.IntByReference;
import net.minecraftforge.fml.common.FMLLog;

/**
 * What the window style code needs to know about the host: the NT build number, which decides whether a DWM
 * attribute exists, and the user's app theme, which decides the title bar colour.
 * <p>
 * Both are read once and cached. Nothing here loads a native library off Windows.
 */
public final class WindowsTheme {

    private static final String PERSONALIZE_KEY =
            "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize";

    private static final String APPS_USE_LIGHT_THEME = "AppsUseLightTheme";

    /** Sentinel for "not read yet"; real build numbers are positive. */
    private static final int UNKNOWN = Integer.MIN_VALUE;

    private static volatile int buildNumber = UNKNOWN;
    private static volatile Boolean appsDarkTheme;

    private WindowsTheme() {
    }

    /**
     * @return whether the modern DWM attributes exist on this machine. Off Windows this is {@code false}, so
     *         callers can treat it as the single platform gate.
     */
    public static boolean isSupported() {
        return Platform.isWindows() && buildNumber() >= DwmApi.MINIMUM_BUILD_NUM;
    }

    /**
     * @return the NT build number, or {@code 0} when it cannot be determined (off Windows, or {@code ntdll} is
     *         unreachable).
     */
    public static int buildNumber() {
        int cached = buildNumber;
        if (cached == UNKNOWN) {
            cached = readBuildNumber();
            buildNumber = cached;
        }
        return cached;
    }

    private static int readBuildNumber() {
        if (!Platform.isWindows()) {
            return 0;
        }
        try {
            final IntByReference majorVersion = new IntByReference();
            final IntByReference build = new IntByReference();
            NtDllHolder.INSTANCE.RtlGetNtVersionNumbers(majorVersion, new IntByReference(), build);
            // The top four bits carry the checked/free build flag, not the build number.
            return build.getValue() & ~0xF0000000;
        } catch (Throwable t) {
            FMLLog.log.debug("Unable to read the Windows build number, assuming none", t);
            return 0;
        }
    }

    private static boolean isAppsUseLightTheme() {
        try {
            if (!Advapi32Util.registryValueExists(
                    WinReg.HKEY_CURRENT_USER,
                    PERSONALIZE_KEY,
                    APPS_USE_LIGHT_THEME)) {
                return true;
            }

            int value = Advapi32Util.registryGetIntValue(
                    WinReg.HKEY_CURRENT_USER,
                    PERSONALIZE_KEY,
                    APPS_USE_LIGHT_THEME);

            return value != 0;
        } catch (Throwable t) {
            return true;
        }
    }

    public static boolean isAppsDarkTheme() {
        Boolean cached = appsDarkTheme;
        if (cached == null) {
            cached = Platform.isWindows() && !isAppsUseLightTheme();
            appsDarkTheme = cached;
        }
        return cached;
    }

    /** {@code ntdll}, loaded on first use so a non-Windows JVM never resolves it. */
    interface NtDll extends Library {
        void RtlGetNtVersionNumbers(
                IntByReference MajorVersion,
                IntByReference MinorVersion,
                IntByReference BuildNumber
        );
    }

    private static final class NtDllHolder {
        private static final NtDll INSTANCE = Platform.isWindows() ? Native.load("ntdll", NtDll.class) : null;
    }
}
