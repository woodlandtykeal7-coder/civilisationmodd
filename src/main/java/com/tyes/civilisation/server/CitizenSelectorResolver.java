package com.tyes.civilisation.server;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.tyes.civilisation.citizen.CitizenRecord;
import com.tyes.civilisation.citizen.CitizenRegistry;
import com.tyes.civilisation.entity.CivilianEntity;
import com.tyes.civilisation.persistence.CitizenSavedData;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** One reusable parser for every citizen-facing command selector. */
public final class CitizenSelectorResolver {
    private CitizenSelectorResolver() {}

    public record Result(CitizenRecord citizen, String error) {
        public boolean success() { return citizen != null; }
        static Result found(CitizenRecord citizen) { return new Result(citizen, ""); }
        static Result failed(String error) { return new Result(null, error); }
    }

    public static Result resolve(CommandSourceStack source, String selector) {
        CitizenRegistry registry = CitizenSavedData.get(source.getServer()).registry();
        if (selector.equalsIgnoreCase("nearest")) return nearest(source, registry);
        if (selector.equalsIgnoreCase("looking")) return looking(source, registry);

        Optional<CitizenRecord> readable = registry.findByReadableId(selector);
        if (readable.isPresent()) return Result.found(readable.orElseThrow());
        try {
            Optional<CitizenRecord> uuid = registry.find(UUID.fromString(selector));
            if (uuid.isPresent()) return Result.found(uuid.orElseThrow());
        } catch (IllegalArgumentException ignored) {
            // Continue to human-name lookup.
        }
        Collection<CitizenRecord> names = registry.findByName(selector);
        if (names.size() == 1) return Result.found(names.iterator().next());
        if (names.size() > 1) {
            String ids = names.stream().map(CitizenRecord::readableId).sorted().reduce((a, b) -> a + ", " + b).orElse("");
            return Result.failed("Citizen name '" + selector + "' is ambiguous; use one of: " + ids);
        }
        return Result.failed("No citizen matches '" + selector + "'");
    }

    private static Result nearest(CommandSourceStack source, CitizenRegistry registry) {
        Vec3 origin = source.getPosition();
        return source.getLevel().getEntitiesOfClass(CivilianEntity.class,
                        AABB.ofSize(origin, 128.0D, 128.0D, 128.0D), e -> e.citizenId().isPresent())
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(origin)))
                .flatMap(e -> registry.find(e.citizenId().orElseThrow()))
                .map(Result::found).orElseGet(() -> Result.failed("No loaded citizen is nearby"));
    }

    private static Result looking(CommandSourceStack source, CitizenRegistry registry) {
        Entity viewer = source.getEntity();
        if (viewer == null) return Result.failed("'looking' requires an in-world command source");
        Vec3 start = viewer.getEyePosition();
        Vec3 end = start.add(viewer.getLookAngle().scale(32.0D));
        ServerLevel level = source.getLevel();
        return level.getEntitiesOfClass(CivilianEntity.class,
                        viewer.getBoundingBox().expandTowards(viewer.getLookAngle().scale(32.0D)).inflate(1.0D),
                        e -> e != viewer && e.citizenId().isPresent() && e.getBoundingBox().inflate(0.35D).clip(start, end).isPresent())
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(viewer)))
                .flatMap(e -> registry.find(e.citizenId().orElseThrow()))
                .map(Result::found).orElseGet(() -> Result.failed("You are not looking at a citizen"));
    }

    public static CompletableFuture<Suggestions> suggest(CommandSourceStack source, SuggestionsBuilder builder) {
        builder.suggest("nearest").suggest("looking");
        CitizenSavedData.get(source.getServer()).registry().all().forEach(c -> {
            builder.suggest(c.readableId());
            if (!c.displayName().contains(" ")) builder.suggest(c.displayName());
            builder.suggest(c.citizenId().toString());
        });
        return builder.buildFuture();
    }
}
