package fr.maxlego08.head.api;

import fr.maxlego08.head.api.enums.HeadCategory;

/** Compile-only stub. Runtime uses the real zHead plugin. */
public interface Head {
    String getName();
    HeadCategory getHeadCategory();
    String getId();
    String getValue();
    String getTags();
}
