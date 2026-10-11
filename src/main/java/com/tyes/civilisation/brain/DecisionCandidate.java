package com.tyes.civilisation.brain;

public record DecisionCandidate(CitizenTask task, int score, String reason) {
}
