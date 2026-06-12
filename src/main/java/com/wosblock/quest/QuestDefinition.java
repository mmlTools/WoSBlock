package com.wosblock.quest;

import java.util.Map;
import org.bukkit.Material;

public record QuestDefinition(
    String id,
    String displayName,
    QuestSchedule schedule,
    QuestType type,
    Material material,
    int amount,
    double rewardMoney,
    int rewardXp,
    Map<String, Integer> rewardItems
) {
}
