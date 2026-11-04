/// Copyright under MIT https://github.com/LemonCaramel/Mica
package net.minecraftforge.client.windows;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.PointerType;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.ptr.IntByReference;
import net.minecraftforge.common.ForgeEarlyConfig;
import net.minecraftforge.fml.common.FMLLog;
import org.lwjgl.sdl.SDLProperties;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.system.NativeType;

/**
 * The Windows DWM window attributes behind {@code [early.modern_windows_styles]}: dark title bar, backdrop
 * material, rounded corners, border/caption/text colour.
 * <p>
 * SDL3 owns none of these. Its Win32 backend loads {@code dwmapi} itself, but only ever writes
 * {@code DWMWA_WINDOW_CORNER_PREFERENCE} and {@code DWMWA_BORDER_COLOR}, and only with fixed values around a
 * fullscreen switch. So the attributes are set here, on the HWND that SDL hands out through
 * {@code SDL_PROP_WINDOW_WIN32_HWND_POINTER}, and the borderless path never trips SDL's own writes.
 * <p>
 * The backdrop material is deliberately limited to the window frame (the title bar of a decorated window).
 * Extending the DWM frame over the client area would hand the game surface to the compositor, and Minecraft 1.12
 * is not written for that: it clears with alpha 0 and its GUI blending leaves the destination alpha near zero, so
 * the whole canvas blends into the system backdrop and only the few fully opaque pixels survive. The material is a
 * frame decoration here, not a window transparency effect.
 * <p>
 * Nothing in this class touches a native library off Windows: the JNA library is loaded lazily, so a class
 * reference from shared code cannot fail on a machine without {@code dwmapi}.
 */
public interface DwmApi extends Library {

    /* DWM API */
    DwmApi INSTANCE = Holder.INSTANCE;
    int INT_SIZE = 4;

    /** Lazily loaded so merely referencing {@link DwmApi} on a non-Windows machine loads no library. */
    final class Holder {
        private static final DwmApi INSTANCE = Platform.isWindows() ? Native.load("dwmapi", DwmApi.class) : null;

        private Holder() {}
    }

    /* BOOL */
    int BOOL_FALSE = 0;
    int BOOL_TRUE = 1;

    /* DWMWINDOWATTRIBUTE */
    int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;
    int DWMWA_WINDOW_CORNER_PREFERENCE = 33;
    int DWMWA_BORDER_COLOR = 34;
    int DWMWA_CAPTION_COLOR = 35;
    int DWMWA_TEXT_COLOR = 36;
    int DWMWA_SYSTEMBACKDROP_TYPE = 38;

    /* Windows build numbers */
    /** First build where the modern DWM attributes (corner, border, caption, text) exist. */
    int MINIMUM_BUILD_NUM = 22000;
    /** First build that understands {@code DWMWA_SYSTEMBACKDROP_TYPE} (Mica and friends). */
    int BACKDROP_BUILD_NUM = 22621;

    /* DWM_SYSTEMBACKDROP_TYPE */
    enum DWM_SYSTEMBACKDROP_TYPE {
        DWMSBT_AUTO(0, "auto"), // 0 Auto
        DWMSBT_NONE(1, "none"), // 1 None
        DWMSBT_MAINWINDOW(2, "mica"), // 2 Mica
        DWMSBT_TRANSIENTWINDOW(3, "acrylic"), // 3 Acrylic
        DWMSBT_TABBEDWINDOW(4, "tabbed"); // 4 Tabbed

        public final int value;
        public final String translate;

        DWM_SYSTEMBACKDROP_TYPE(int value, String translate) {
            this.value = value;
            this.translate = translate;
        }

        /** A resize re-reads the config, so a bad value must not warn once per event. */
        private static int lastWarned = Integer.MIN_VALUE;

        /** Reads a value written by hand into {@code forge.cfg}: a number outside the enum is a typo, not a reason
         *  to crash the client before the main menu. */
        public static DWM_SYSTEMBACKDROP_TYPE fromInt(int v) {
            for (var e : values()) {
                if (e.value == v) return e;
            }
            if (lastWarned != v) {
                lastWarned = v;
                FMLLog.log.warn("Unknown systemBackdropType {}, expected 0-4; using {}.", v, DWMSBT_AUTO.translate);
            }
            return DWMSBT_AUTO;
        }
    }

    /* DWM_WINDOW_CORNER_PREFERENCE */
    enum DWM_WINDOW_CORNER_PREFERENCE {
        DWMWCP_DEFAULT(0, "default"), // 0
        DWMWCP_DONOTROUND(1, "do_not_round"), // 1
        DWMWCP_ROUND(2, "round"), // 2
        DWMWCP_ROUNDSMALL(3, "round_small"); // 3

        public final int value;
        public final String translate;

        DWM_WINDOW_CORNER_PREFERENCE(int value, String translate) {
            this.value = value;
            this.translate = translate;
        }

        /** A resize re-reads the config, so a bad value must not warn once per event. */
        private static int lastWarned = Integer.MIN_VALUE;

        /** See {@link DWM_SYSTEMBACKDROP_TYPE#fromInt(int)}. */
        public static DWM_WINDOW_CORNER_PREFERENCE fromInt(int v) {
            for (var e : values()) {
                if (e.value == v) return e;
            }
            if (lastWarned != v) {
                lastWarned = v;
                FMLLog.log.warn("Unknown windowCorner {}, expected 0-3; using {}.", v, DWMWCP_DEFAULT.translate);
            }
            return DWMWCP_DEFAULT;
        }
    }

    /* DWMWA_BORDER_COLOR_OPTION */
    int DWMWA_COLOR_NONE = 0xFFFFFFFE;
    int DWMWA_COLOR_DEFAULT = 0xFFFFFFFF;

    @NativeType("HRESULT") // Err
    int DwmSetWindowAttribute(
            HWND hwnd,
            int dwAttribute,
            PointerType pvAttribute,
            int cbAttribute
    );

    /**
     * Applies the configured window style to the game window.
     * <p>
     * Called from {@code createDisplay} and again whenever the client area gains a size: DWM needs a non-empty
     * client area before the backdrop material shows, and a window that is never resized never sends the message
     * that would otherwise carry the first chance to set it.
     *
     * @param fullscreen whether the window currently covers the display borderless
     * @param sdlWindow  the SDL window handle, as returned by {@code Display.getWindow()}
     */
    static void updateDwm(final boolean fullscreen, final long sdlWindow) {
        final HWND hwnd = hwndOf(sdlWindow);
        if (hwnd == null) {
            return;
        }
        if (fullscreen) {
            disableWindowEffect(hwnd);
            return;
        }

        // DWMWA_USE_IMMERSIVE_DARK_MODE
        final boolean useImmersiveDarkMode = ForgeEarlyConfig.modernWindowsStyles.useImmersiveDarkMode
                && WindowsTheme.isAppsDarkTheme();
        setAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, useImmersiveDarkMode ? BOOL_TRUE : BOOL_FALSE);

        // DWMWA_SYSTEMBACKDROP_TYPE
        final DWM_SYSTEMBACKDROP_TYPE systemBackdropType = DWM_SYSTEMBACKDROP_TYPE
                .fromInt(ForgeEarlyConfig.modernWindowsStyles.systemBackdropType);
        if (supportsBackdrop()) {
            setAttribute(hwnd, DWMWA_SYSTEMBACKDROP_TYPE, systemBackdropType.ordinal());
        } else if (systemBackdropType != DWM_SYSTEMBACKDROP_TYPE.DWMSBT_AUTO
                && systemBackdropType != DWM_SYSTEMBACKDROP_TYPE.DWMSBT_NONE) {
            FMLLog.log.warn(
                "systemBackdropType '{}' needs Windows 11 build {} or newer (this is build {}); ignoring it.",
                systemBackdropType.translate,
                BACKDROP_BUILD_NUM,
                WindowsTheme.buildNumber());
        }

        // DWMWA_WINDOW_CORNER_PREFERENCE
        final DWM_WINDOW_CORNER_PREFERENCE windowCorner = DWM_WINDOW_CORNER_PREFERENCE
                .fromInt(ForgeEarlyConfig.modernWindowsStyles.windowCorner);
        setAttribute(hwnd, DWMWA_WINDOW_CORNER_PREFERENCE, windowCorner.ordinal());

        // DWMWA_BORDER_COLOR
        if (ForgeEarlyConfig.modernWindowsStyles.useDefaultBorder) {
            setAttribute(hwnd, DWMWA_BORDER_COLOR, DWMWA_COLOR_DEFAULT);
        } else if (ForgeEarlyConfig.modernWindowsStyles.hideWindowBorder) {
            setAttribute(hwnd, DWMWA_BORDER_COLOR, DWMWA_COLOR_NONE);
        } else {
            setAttribute(hwnd, DWMWA_BORDER_COLOR, convert(ForgeEarlyConfig.modernWindowsStyles.borderColor));
        }

        // DWMWA_CAPTION_COLOR
        if (ForgeEarlyConfig.modernWindowsStyles.useDefaultCaption) {
            setAttribute(hwnd, DWMWA_CAPTION_COLOR, DWMWA_COLOR_DEFAULT);
        } else {
            setAttribute(hwnd, DWMWA_CAPTION_COLOR, convert(ForgeEarlyConfig.modernWindowsStyles.captionColor));
        }

        // DWMWA_TEXT_COLOR
        if (ForgeEarlyConfig.modernWindowsStyles.useDefaultText) {
            setAttribute(hwnd, DWMWA_TEXT_COLOR, DWMWA_COLOR_DEFAULT);
        } else {
            setAttribute(hwnd, DWMWA_TEXT_COLOR, convert(ForgeEarlyConfig.modernWindowsStyles.textColor));
        }
    }

    /**
     * Resolves the Win32 window behind an SDL window.
     *
     * @return the HWND, or {@code null} when the window is gone, the backend is not Win32, or the process is not
     *         on Windows at all.
     */
    static HWND hwndOf(final long sdlWindow) {
        if (sdlWindow == 0L || !Platform.isWindows() || !WindowsTheme.isSupported()) {
            return null;
        }
        final long nativeHandle = SDLProperties.SDL_GetPointerProperty(
            SDLVideo.SDL_GetWindowProperties(sdlWindow),
            SDLVideo.SDL_PROP_WINDOW_WIN32_HWND_POINTER,
            0L);
        if (nativeHandle == 0L) {
            return null;
        }
        return new HWND(Pointer.createConstant(nativeHandle));
    }

    /** @return whether this Windows build knows {@code DWMWA_SYSTEMBACKDROP_TYPE}. */
    static boolean supportsBackdrop() {
        return WindowsTheme.isSupported() && WindowsTheme.buildNumber() >= BACKDROP_BUILD_NUM;
    }

    static void disableWindowEffect(final HWND hwnd) {
        // ... DWMWA_USE_IMMERSIVE_DARK_MODE
        if (supportsBackdrop()) {
            setAttribute(hwnd, DWMWA_SYSTEMBACKDROP_TYPE, DWM_SYSTEMBACKDROP_TYPE.DWMSBT_AUTO.ordinal());
        }
        setAttribute(hwnd, DWMWA_WINDOW_CORNER_PREFERENCE, DWM_WINDOW_CORNER_PREFERENCE.DWMWCP_DEFAULT.ordinal());
        setAttribute(hwnd, DWMWA_BORDER_COLOR, DWMWA_COLOR_DEFAULT);
        setAttribute(hwnd, DWMWA_CAPTION_COLOR, DWMWA_COLOR_DEFAULT);
        setAttribute(hwnd, DWMWA_TEXT_COLOR, DWMWA_COLOR_DEFAULT);
    }

    /** Writes one DWORD attribute. DWM answers {@code E_INVALIDARG} for attributes a build does not know. */
    private static void setAttribute(final HWND hwnd, final int attribute, final int value) {
        if (INSTANCE == null) {
            return;
        }
        final int hr = INSTANCE.DwmSetWindowAttribute(hwnd, attribute, new IntByReference(value), INT_SIZE);
        if (hr < 0) {
            FMLLog.log.debug("DwmSetWindowAttribute({}) failed: 0x{}", attribute, String.format("%08X", hr));
        }
    }

    private static int convert(final int color) {
        // Ignore Alpha
        final int b = (color >> 16) & 0xFF;
        final int g = (color >> 8) & 0xFF;
        final int r = color & 0xFF;

        return ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }
}
