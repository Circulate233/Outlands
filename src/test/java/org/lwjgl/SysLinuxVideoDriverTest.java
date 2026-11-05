package org.lwjgl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The Linux driver choice runs before SDL is initialised, so it has to be a pure decision: a Wayland session uses
 * the Wayland driver, everything else is left to SDL unless the player pinned a driver.
 */
class SysLinuxVideoDriverTest {

    @Test
    void aWaylandSessionUsesTheWaylandDriverByDefault() {
        assertEquals("wayland", Sys.resolveLinuxVideoDriver("auto", true));
    }

    @Test
    void aSessionWithoutWaylandLeavesTheChoiceToSdl() {
        assertNull(Sys.resolveLinuxVideoDriver("auto", false), "SDL's own order still ends up on X11/XWayland");
        assertNull(Sys.resolveLinuxVideoDriver(null, false), "a missing setting behaves like auto");
    }

    @Test
    void anExplicitChoiceWinsOverTheSession() {
        assertEquals("wayland", Sys.resolveLinuxVideoDriver("wayland", false));
        assertEquals("x11", Sys.resolveLinuxVideoDriver("x11", true));
        assertEquals("x11", Sys.resolveLinuxVideoDriver("xorg", true), "xorg is the SDL name for the X11 driver");
    }

    @Test
    void theSettingIsTrimmedAndCaseInsensitive() {
        assertEquals("wayland", Sys.resolveLinuxVideoDriver("  Wayland ", false));
        assertEquals("wayland", Sys.resolveLinuxVideoDriver("AUTO", true));
    }

    @Test
    void anUnknownSettingFallsBackToAuto() {
        assertNull(Sys.resolveLinuxVideoDriver("mir", false));
        assertEquals("wayland", Sys.resolveLinuxVideoDriver("", true));
    }
}
