package org.lwjgl.input;

public class KeyEvent {

    public int key;
    public char aChar;
    public KeyState state;
    public long nano;
    final InputSnapshot snapshot;

    public KeyEvent(int key, char c, KeyState state, long nano) {
        this(key, c, state, nano, InputSnapshot.current());
    }

    KeyEvent(int key, char c, KeyState state, long nano, InputSnapshot snapshot) {
        this.key = key;
        this.aChar = c;
        this.state = state;
        this.nano = nano;
        this.snapshot = snapshot;
    }
}
