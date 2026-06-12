package com.wosblock.quest;

public final class QuestProgress {
    private final QuestDefinition definition;
    private int progress;
    private long completedAt;

    public QuestProgress(QuestDefinition definition) {
        this.definition = definition;
    }

    public QuestProgress(QuestDefinition definition, int progress) {
        this.definition = definition;
        this.progress = Math.max(0, Math.min(definition.amount(), progress));
    }

    public QuestDefinition definition() {
        return definition;
    }

    public int progress() {
        return progress;
    }

    public void addProgress(int amount) {
        progress = Math.min(definition.amount(), progress + Math.max(0, amount));
    }

    public void progress(int progress) {
        this.progress = Math.max(0, Math.min(definition.amount(), progress));
    }

    public boolean complete() {
        return progress >= definition.amount();
    }

    public long completedAt() {
        return completedAt;
    }

    public void completedAt(long completedAt) {
        this.completedAt = completedAt;
    }

    public void reset() {
        progress = 0;
    }
}
