package net.minecraftforge.client;

import net.minecraftforge.fml.common.FMLLog;
import org.lwjgl.MainThread;
import org.lwjgl.opengl.Display;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLVideo;

/**
 * Drives the taskbar loading indicator through SDL's progress API.
 * <p>
 * This replaces the direct Win32 ITaskbarList3 calls: SDL maps the same state onto each desktop's
 * native indicator (Windows taskbar, Unity launcher, and others), so no HWND is needed.
 * <p>
 * Desktops without such an indicator (Wayland, a bare X11 session, macOS) report the API as unsupported. The
 * indicator is cosmetic, so those failures are ignored - reporting them once - instead of failing the launch.
 */
public final class TaskbarProgress {

    private static volatile boolean unavailableReported = false;

    private TaskbarProgress() {}

    /** Shows an indeterminate indicator, for phases without a meaningful ratio. */
    public static void setIndeterminate() {
        setState(SDLVideo.SDL_PROGRESS_STATE_INDETERMINATE);
    }

    /** Shows a determinate indicator. */
    public static void setNormal() {
        setState(SDLVideo.SDL_PROGRESS_STATE_NORMAL);
    }

    private static void setState(int state) {
        if (!Display.isCreated()) {
            return;
        }
        final long window = Display.getWindow();
        MainThread.runOnMainThread(() -> reportOnce(SDLVideo.SDL_SetWindowProgressState(window, state)));
    }

    /**
     * @param completed units done
     * @param total     units total
     */
    public static void setValue(long completed, long total) {
        if (!Display.isCreated() || total <= 0) {
            return;
        }
        final long window = Display.getWindow();
        final float value = (float) Math.max(0.0, Math.min(1.0, (double) completed / (double) total));
        MainThread.runOnMainThread(() -> reportOnce(SDLVideo.SDL_SetWindowProgressValue(window, value)));
    }

    /** Clears the indicator. */
    public static void clear() {
        if (!Display.isCreated()) {
            return;
        }
        final long window = Display.getWindow();
        MainThread.runOnMainThread(
            () -> reportOnce(SDLVideo.SDL_SetWindowProgressState(window, SDLVideo.SDL_PROGRESS_STATE_NONE)));
    }

    /** The desktop does not expose a progress indicator: log the reason once and carry on without one. */
    private static void reportOnce(boolean result) {
        if (result || unavailableReported) {
            return;
        }
        unavailableReported = true;
        FMLLog.log.debug("Taskbar progress not supported by this desktop ({}), continuing without it",
            SDLError.SDL_GetError());
    }
}
