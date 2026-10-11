package com.tyes.civilisation.construction.project;
import com.tyes.civilisation.construction.style.ArchitectureStyleId;import java.util.*;
public record HeritageRecord(ArchitectureStyleId originalStyle,UUID originalSettlementId,Optional<ArchitectureStyleId> restorationStyle,int constructionEra,int restorationEra){}
