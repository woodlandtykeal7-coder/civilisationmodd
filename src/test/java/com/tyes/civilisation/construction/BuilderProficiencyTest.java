package com.tyes.civilisation.construction;

import com.tyes.civilisation.brain.task.BuilderTask;
import com.tyes.civilisation.society.CareerLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class BuilderProficiencyTest {
    @Test void everyProficiencyRemainsVisibleButImprovesPlacementTime() {
        assertEquals(40, BuilderTask.workTicksFor(CareerLevel.NOVICE));
        assertEquals(32, BuilderTask.workTicksFor(CareerLevel.TRAINED));
        assertEquals(24, BuilderTask.workTicksFor(CareerLevel.EXPERIENCED));
        assertEquals(19, BuilderTask.workTicksFor(CareerLevel.MASTER));
    }
}
