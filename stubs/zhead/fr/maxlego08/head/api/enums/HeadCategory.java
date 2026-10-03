package fr.maxlego08.head.api.enums;

/** Compile-only stub. Runtime uses the real zHead plugin. */
public enum HeadCategory {
    ALPHABET,
    ANIMALS,
    BLOCKS,
    DECORATION,
    FOOD_DRINKS,
    HUMANS,
    HUMANOID,
    MONSTERS,
    PLANTS,
    MISCELLANEOUS;

    public static HeadCategory fromString(String name) {
        return valueOf(name.toUpperCase());
    }

    public String getName() {
        return name();
    }

    public String getUrl() {
        return "";
    }
}
