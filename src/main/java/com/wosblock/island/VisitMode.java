package com.wosblock.island;

public enum VisitMode {
    FRIENDLY(false, false),
    CARNAGE(true, false),
    DEMOLITION(false, true),
    FFA(true, true);

    private final boolean combatAllowed;
    private final boolean blockBreakingAllowed;

    VisitMode(boolean combatAllowed, boolean blockBreakingAllowed) {
        this.combatAllowed = combatAllowed;
        this.blockBreakingAllowed = blockBreakingAllowed;
    }

    public boolean combatAllowed() {
        return combatAllowed;
    }

    public boolean blockBreakingAllowed() {
        return blockBreakingAllowed;
    }
}
