package com.tyes.civilisation.knowledge.institution;

import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Future archive/library boundary. Implementations must only contain deliberately recorded knowledge. */
public interface InstitutionKnowledgeStore {
    UUID institutionId();
    UUID settlementId();
    Map<ResourceLocation, Integer> concepts();
}
