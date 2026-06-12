package com.wosblock.npc;

public enum CoreNpcType {
    AUCTIONEER,
    THIEF,
    CLERK;

    public static CoreNpcType fromConfigKey(String key) {
        return CoreNpcType.valueOf(key.toUpperCase());
    }
}
