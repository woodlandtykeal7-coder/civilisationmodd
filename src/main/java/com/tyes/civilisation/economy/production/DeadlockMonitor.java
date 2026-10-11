package com.tyes.civilisation.economy.production;

import com.tyes.civilisation.CivilisationMod;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Runtime-only, rate-limited stalled-request detector. It diagnoses; it never creates resources. */
public final class DeadlockMonitor {
    private static final long STALL_TICKS = 2400L;
    private final Map<UUID, Progress> progress = new HashMap<>();
    public void observe(ProductionRequest request, long now) {
        if (request.state() == ProductionState.COMPLETE || request.state() == ProductionState.CANCELLED) {
            progress.remove(request.requestId()); return;
        }
        String signature = request.state()+":"+request.blocker()+":"+request.completedQuantity()+":"+request.claimedBy().orElse(null);
        Progress old = progress.get(request.requestId());
        if (old == null || !old.signature.equals(signature)) { progress.put(request.requestId(), new Progress(signature, now, Long.MIN_VALUE)); return; }
        if (now - old.since >= STALL_TICKS && now - old.lastWarning >= STALL_TICKS) {
            CivilisationMod.LOGGER.warn("[Economy] Production {} for {} appears stalled since tick {}: state={}, blocker={}",
                    request.requestId(), request.output(), old.since, request.state(), request.blocker());
            progress.put(request.requestId(), new Progress(signature, old.since, now));
        }
    }
    private record Progress(String signature, long since, long lastWarning) { }
}
