package net.minecraftforge.client.windows;

import net.minecraftforge.client.windows.DwmApi.DWM_SYSTEMBACKDROP_TYPE;
import net.minecraftforge.client.windows.DwmApi.DWM_WINDOW_CORNER_PREFERENCE;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The two values that reach {@code DwmSetWindowAttribute} come straight from {@code forge.cfg}, which a player
 * can edit. They used to be looked up with a method that threw on anything outside the enum, which killed the
 * client before the main menu and looked like a mod crash.
 */
class DwmApiTest {

    @Test
    void knownBackdropValuesResolve() {
        assertEquals(DWM_SYSTEMBACKDROP_TYPE.DWMSBT_AUTO, DWM_SYSTEMBACKDROP_TYPE.fromInt(0));
        assertEquals(DWM_SYSTEMBACKDROP_TYPE.DWMSBT_NONE, DWM_SYSTEMBACKDROP_TYPE.fromInt(1));
        assertEquals(DWM_SYSTEMBACKDROP_TYPE.DWMSBT_MAINWINDOW, DWM_SYSTEMBACKDROP_TYPE.fromInt(2));
        assertEquals(DWM_SYSTEMBACKDROP_TYPE.DWMSBT_TRANSIENTWINDOW, DWM_SYSTEMBACKDROP_TYPE.fromInt(3));
        assertEquals(DWM_SYSTEMBACKDROP_TYPE.DWMSBT_TABBEDWINDOW, DWM_SYSTEMBACKDROP_TYPE.fromInt(4));
    }

    @Test
    void anOutOfRangeBackdropFallsBackInsteadOfThrowing() {
        assertEquals(DWM_SYSTEMBACKDROP_TYPE.DWMSBT_AUTO, DWM_SYSTEMBACKDROP_TYPE.fromInt(99));
        assertEquals(DWM_SYSTEMBACKDROP_TYPE.DWMSBT_AUTO, DWM_SYSTEMBACKDROP_TYPE.fromInt(-1));
    }

    @Test
    void knownCornerValuesResolve() {
        assertEquals(DWM_WINDOW_CORNER_PREFERENCE.DWMWCP_DEFAULT, DWM_WINDOW_CORNER_PREFERENCE.fromInt(0));
        assertEquals(DWM_WINDOW_CORNER_PREFERENCE.DWMWCP_DONOTROUND, DWM_WINDOW_CORNER_PREFERENCE.fromInt(1));
        assertEquals(DWM_WINDOW_CORNER_PREFERENCE.DWMWCP_ROUND, DWM_WINDOW_CORNER_PREFERENCE.fromInt(2));
        assertEquals(DWM_WINDOW_CORNER_PREFERENCE.DWMWCP_ROUNDSMALL, DWM_WINDOW_CORNER_PREFERENCE.fromInt(3));
    }

    @Test
    void anOutOfRangeCornerFallsBackInsteadOfThrowing() {
        assertEquals(DWM_WINDOW_CORNER_PREFERENCE.DWMWCP_DEFAULT, DWM_WINDOW_CORNER_PREFERENCE.fromInt(42));
        assertEquals(DWM_WINDOW_CORNER_PREFERENCE.DWMWCP_DEFAULT, DWM_WINDOW_CORNER_PREFERENCE.fromInt(-7));
    }

    /** The enum ordinals are what gets written to DWM, so they have to match the documented values. */
    @Test
    void ordinalsMatchTheDwmConstants() {
        assertEquals(2, DWM_SYSTEMBACKDROP_TYPE.DWMSBT_MAINWINDOW.ordinal());
        assertEquals(3, DWM_SYSTEMBACKDROP_TYPE.DWMSBT_TRANSIENTWINDOW.ordinal());
        assertEquals(2, DWM_WINDOW_CORNER_PREFERENCE.DWMWCP_ROUND.ordinal());
        assertEquals(1, DWM_WINDOW_CORNER_PREFERENCE.DWMWCP_DONOTROUND.ordinal());
    }

    /**
     * Nothing on this test machine is Windows, so the window style code has to stay inert: no library load, no
     * HWND, and a build number that every version check rejects.
     */
    @Test
    void offWindowsTheWindowStyleIsInert() {
        if (com.sun.jna.Platform.isWindows()) {
            return;
        }
        assertFalse(WindowsTheme.isSupported());
        assertEquals(0, WindowsTheme.buildNumber());
        assertFalse(DwmApi.supportsBackdrop());
        assertNull(DwmApi.hwndOf(0L));
        assertNull(DwmApi.INSTANCE);
    }
}
