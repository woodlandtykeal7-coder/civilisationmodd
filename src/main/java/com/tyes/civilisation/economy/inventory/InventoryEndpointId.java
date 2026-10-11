package com.tyes.civilisation.economy.inventory;

import java.util.UUID;

public record InventoryEndpointId(UUID value) {
    public static InventoryEndpointId create() { return new InventoryEndpointId(UUID.randomUUID()); }
    @Override public String toString() { return value.toString(); }
}
