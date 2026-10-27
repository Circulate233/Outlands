package net.minecraftforge.client.ime;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import org.lwjgl.input.Keyboard;

/**
 * The "make the input method reachable" key.
 * <p>
 * SDL keeps the operating system's input method detached from the window while its text input session is closed,
 * and the session only opens for things the game recognises as text input: a focused {@link
 * net.minecraft.client.gui.GuiTextField}, a book or a sign. A mod screen that draws its own text widget is none of
 * those, so switching to an input method there can be impossible. This key lets the player ask for the session.
 * <p>
 * It is a {@link KeyBinding} so it appears under Options - Controls and can be rebound, but the match happens in the
 * SDL event layer: {@code KeyInputEvent} is only fired while {@code currentScreen == null ||
 * currentScreen.allowUserInput}, and {@code GuiScreen.allowUserInput} is false for every screen that is not a chat box
 * - exactly the screens this key exists for.
 * <p>
 * The binding carries Ctrl as its chord so that the controls screen names the combination instead of showing a bare
 * key. That is what the chord is doing here: the match itself stays in the event layer, which reports the Ctrl that
 * was held at the event and accepts either side of it, neither of which a binding can express.
 */
public final class ImeForceKey {

    /**
     * Bound to Space and required to be held with Ctrl, which makes Ctrl+Space - the combination input methods use to
     * switch to themselves. Rebinding changes only the second half of the combination.
     */
    public static final KeyBinding FORCE_IME = new KeyBinding(
        "key.cleanroommc.forceIme", Keyboard.KEY_SPACE, "key.categories.cleanroommc");

    static {
        // Both as the default and as the current chord, so that resetting the binding restores the combination too.
        FORCE_IME.setDefaultChordKeys(Keyboard.KEY_LCONTROL);
        FORCE_IME.setChordKeys(Keyboard.KEY_LCONTROL);
    }

    private ImeForceKey() {}

    public static void register() {
        ClientRegistry.registerKeyBinding(FORCE_IME);
    }

    /**
     * Called from the SDL key event with the event's own modifier state, so it fires even while a screen that ignores
     * key input is open, and so a key held from before the event cannot be mistaken for the modifier.
     */
    public static void onKeyDown(int lwjglKey, boolean ctrlHeld, boolean altHeld, boolean metaHeld) {
        if (lwjglKey != FORCE_IME.getKeyCode()) {
            return;
        }
        // AltGr reports Ctrl too; the alt/meta checks keep that from being read as the combination.
        if (!ctrlHeld || altHeld || metaHeld) {
            return;
        }
        TextFieldHandler.forceTextInput();
    }
}
