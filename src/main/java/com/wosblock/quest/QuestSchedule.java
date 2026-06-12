package com.wosblock.quest;

import java.time.Duration;

public enum QuestSchedule {
    ONCE(Duration.ZERO),
    REPEATABLE(Duration.ZERO),
    DAILY(Duration.ofDays(1)),
    WEEKLY(Duration.ofDays(7)),
    MONTHLY(Duration.ofDays(30));

    private final Duration cooldown;

    QuestSchedule(Duration cooldown) {
        this.cooldown = cooldown;
    }

    public long cooldownMillis() {
        return cooldown.toMillis();
    }
}
