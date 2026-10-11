package com.tyes.civilisation.brain;

import java.util.Optional;

@FunctionalInterface
public interface DecisionProvider {
    Optional<DecisionCandidate> evaluate(CitizenBrain brain);
}
