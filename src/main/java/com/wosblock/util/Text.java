package com.wosblock.util;

import java.util.Arrays;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class Text {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private Text() {
    }

    public static Component legacy(String value) {
        return LEGACY.deserialize(value == null ? "" : value);
    }

    public static Component plain(String value) {
        return Component.text(value == null ? "" : value);
    }

    public static List<Component> lore(String... lines) {
        return Arrays.stream(lines).map(Text::legacy).toList();
    }

    public static String plainText(Component component) {
        return PLAIN.serialize(component);
    }
}
