package com.tyes.civilisation.construction.demand;

public record HousingStatus(int population, int householdCount, int householdsNeedingHomes,
                            int requiredHouseholdCapacity, int completedCapacity, int futureCapacity,
                            int occupiedCapacity, int availableCapacity, int deficit) {
}
