package com.tyes.civilisation.work;

import com.tyes.civilisation.citizen.AssignedLocation;
import java.util.UUID;

public record WorkAssignment(UUID settlementId, String unitId, WorkType type, AssignedLocation target) { }
