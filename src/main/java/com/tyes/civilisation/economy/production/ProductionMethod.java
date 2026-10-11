package com.tyes.civilisation.economy.production;
public enum ProductionMethod {
    USE_EXISTING_STOCK, CRAFT, SMELT, SMOKE, BLAST, STONECUT, HARVEST, FORESTRY, MINE, UNAVAILABLE;
    public boolean usesFurnace() { return this == SMELT || this == SMOKE || this == BLAST; }
}
