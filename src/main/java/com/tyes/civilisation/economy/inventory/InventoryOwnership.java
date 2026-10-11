package com.tyes.civilisation.economy.inventory;

public enum InventoryOwnership {
    SETTLEMENT, WORKPLACE, HOUSEHOLD, CITIZEN, PLAYER, FOREIGN, UNKNOWN;

    public boolean permitsAutomaticUse() {
        return this == SETTLEMENT || this == WORKPLACE || this == CITIZEN;
    }
}
