package com.tyes.civilisation.economy.forecast;
public record DemandForecast(int population, int nearTermFood, int protectedFood, int seedReserve,
                             int lumberjacks, int usableAxes, int fuelDemand) { }
