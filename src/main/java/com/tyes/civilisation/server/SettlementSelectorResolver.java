package com.tyes.civilisation.server;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.tyes.civilisation.persistence.SettlementSavedData;
import com.tyes.civilisation.settlement.SettlementState;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;

public final class SettlementSelectorResolver {
    private SettlementSelectorResolver() {}
    public record Result(SettlementState settlement, String error) { public boolean success() { return settlement != null; } }
    public static Result resolve(CommandSourceStack source, String selector) {
        var registry = SettlementSavedData.get(source.getServer()).registry();
        Optional<SettlementState> readable = registry.findByReadableId(selector);
        if (readable.isPresent()) return new Result(readable.orElseThrow(), "");
        try {
            Optional<SettlementState> uuid = registry.find(UUID.fromString(selector));
            if (uuid.isPresent()) return new Result(uuid.orElseThrow(), "");
        } catch (IllegalArgumentException ignored) { }
        Collection<SettlementState> names = registry.findByName(selector);
        if (names.size() == 1) return new Result(names.iterator().next(), "");
        if (names.size() > 1) return new Result(null, "Settlement name is ambiguous; use "
                + names.stream().map(SettlementState::readableId).sorted().reduce((a, b) -> a + ", " + b).orElse(""));
        return new Result(null, "No settlement matches '" + selector + "'");
    }
    public static CompletableFuture<Suggestions> suggest(CommandSourceStack source, SuggestionsBuilder builder) {
        SettlementSavedData.get(source.getServer()).registry().all().forEach(s -> {
            builder.suggest(s.readableId());
            if (!s.name().contains(" ")) builder.suggest(s.name());
            builder.suggest(s.settlementId().toString());
        });
        return builder.buildFuture();
    }
}
