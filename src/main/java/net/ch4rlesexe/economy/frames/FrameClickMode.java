package net.ch4rlesexe.economy.frames;

public enum FrameClickMode {
    LEFT, RIGHT, BOTH;

    public boolean allowsLeft() {
        return this == LEFT || this == BOTH;
    }

    public boolean allowsRight() {
        return this == RIGHT || this == BOTH;
    }

    public static FrameClickMode parse(String value, FrameClickMode fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return FrameClickMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
