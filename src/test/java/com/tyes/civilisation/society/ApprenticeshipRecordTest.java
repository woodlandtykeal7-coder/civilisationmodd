package com.tyes.civilisation.society;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tyes.civilisation.settlement.WorkRole;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class ApprenticeshipRecordTest {
    @Test
    void mentorLossPausesAndReplacementCanResumeAfterReload() {
        UUID replacement = UUID.randomUUID();
        ApprenticeshipRecord record = new ApprenticeshipRecord(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), WorkRole.FARMER, 100L);
        record.addExperience(15);
        record.pause();
        assertEquals(ApprenticeshipState.PAUSED_NO_MENTOR, record.state());
        assertTrue(record.mentorId().isEmpty());
        record.assignMentor(replacement);

        ApprenticeshipRecord loaded = ApprenticeshipRecord.load(record.save());
        assertEquals(ApprenticeshipState.ACTIVE, loaded.state());
        assertEquals(replacement, loaded.mentorId().orElseThrow());
        assertEquals(15, loaded.experience());
    }
}
