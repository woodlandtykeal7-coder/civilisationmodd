package com.tyes.civilisation.brain.task;

import com.tyes.civilisation.brain.CitizenActivityState;
import com.tyes.civilisation.brain.CitizenBrain;
import com.tyes.civilisation.brain.CitizenTask;
import com.tyes.civilisation.brain.TaskCancelReason;
import com.tyes.civilisation.brain.TaskResult;
import com.tyes.civilisation.brain.TaskStartResult;
import com.tyes.civilisation.CivilisationMod;
import com.tyes.civilisation.citizen.AssignedLocation;
import com.tyes.civilisation.construction.ConstructionService;
import com.tyes.civilisation.construction.ConstructionInteractionPlanner;
import com.tyes.civilisation.construction.project.ConstructionBlocker;
import com.tyes.civilisation.construction.project.ConstructionProject;
import com.tyes.civilisation.construction.project.ConstructionProjectState;
import com.tyes.civilisation.economy.inventory.PhysicalInventoryOps;
import com.tyes.civilisation.economy.item.ItemKey;
import com.tyes.civilisation.entity.CitizenVisualState;
import com.tyes.civilisation.server.CivilisationServices;
import com.tyes.civilisation.society.CareerLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Physical, one-block-at-a-time construction owned by the citizen brain. */
public final class BuilderTask implements CitizenTask {
    public static final String TYPE = "construction_build";
    private static final int PLACEMENT_COOLDOWN_TICKS = 10;
    private static final double MAX_REACH_SQR = 20.25D;
    private static final int MAX_SITE_REPLANS = 8;
    private static final int MAX_SITE_MOVE_ATTEMPTS = 4;

    private enum Phase { TO_STOCKPILE, TO_SITE, WORKING, COOLDOWN }

    private final UUID projectId;
    private int index;
    private Phase phase;
    private int age;
    private int phaseTicks;
    private Vec3 last = Vec3.ZERO;
    private final ConstructionInteractionPlanner.FailureMemory failedPositions =
            new ConstructionInteractionPlanner.FailureMemory();
    private BlockPos interactionPosition;
    // Ephemeral working-space lease: unlike project claims it never holds physical resources.
    private BlockPos leasedStance;
    private BlockPos previousSample;
    private BlockPos twoSamplesAgo;
    private int oscillationSamples;
    private int noProgressChecks;
    private int siteReplans;
    private double bestTargetDistance = Double.MAX_VALUE;
    private boolean currentPathComplete;

    public BuilderTask(ConstructionService.Claim claim) {
        this(claim.projectId(), claim.blockIndex(), Phase.TO_STOCKPILE, 0, 0);
    }

    private BuilderTask(UUID projectId, int index, Phase phase, int age, int phaseTicks) {
        this.projectId = projectId;
        this.index = index;
        this.phase = phase;
        this.age = age;
        this.phaseTicks = phaseTicks;
    }

    @Override public String type() { return TYPE; }
    @Override public CitizenActivityState activity() { return CitizenActivityState.WORKING; }
    @Override public boolean resumable() { return true; }

    @Override
    public TaskStartResult start(CitizenBrain brain) {
        ConstructionProject project = project(brain);
        if (project == null || !project.owns(index, brain.record().citizenId())) return TaskStartResult.INVALID;
        phase = ownsCargoForTarget(brain, project) ? Phase.TO_SITE : Phase.TO_STOCKPILE;
        resetInteractionPlanning(brain);
        showCargo(brain, project);
        last = brain.entity().position();
        boolean moving = phase == Phase.TO_STOCKPILE
                ? move(brain, project.stockpile())
                : moveToSite(brain, project);
        return moving ? TaskStartResult.STARTED : TaskStartResult.PATH_FAILED;
    }

    @Override
    public TaskResult tick(CitizenBrain brain) {
        failedPositions.tick();
        if (leasedStance != null && (phase == Phase.TO_SITE || phase == Phase.WORKING)) {
            var leaseService = CivilisationServices.constructionStances(brain.level().getServer());
            if (!leaseService.claim(projectId, brain.level().dimension().location(),
                    leasedStance, brain.record().citizenId(), brain.level().getGameTime())) {
                // Another live worker may have acquired a lease after this NPC suspended.
                leasedStance = null;
                interactionPosition = null;
                phase = Phase.TO_SITE;
                brain.entity().getNavigation().stop();
            }
        }
        ConstructionProject project = project(brain);
        UUID citizen = brain.record().citizenId();
        if (project == null || phase != Phase.COOLDOWN && !project.owns(index, citizen)) return TaskResult.FAILED;
        if (!project.anchor().isIn(brain.level()) || !brain.level().isLoaded(project.anchor().pos())) {
            project.setState(ConstructionProjectState.BLOCKED, ConstructionBlocker.CHUNK_UNLOADED);
            return TaskResult.WAITING;
        }

        if (phase == Phase.COOLDOWN) {
            brain.entity().setTaskVisualState(CitizenVisualState.IDLE);
            if (++phaseTicks < PLACEMENT_COOLDOWN_TICKS) return TaskResult.RUNNING;
            return advance(brain, project);
        }

        var planned = project.blueprint().blocks().get(index);
        ItemKey key = ItemKey.parse(planned.blockId().toString());
        AssignedLocation target = target(project);
        Block expected = BuiltInRegistries.BLOCK.get(planned.blockId());
        if (brain.level().getBlockState(target.pos()).is(expected)) {
            service(brain).placed(project, index, citizen);
            return advance(brain, project);
        }

        if (phase == Phase.TO_STOCKPILE) return collectMaterial(brain, project, key, planned.blockId());
        if (phase == Phase.TO_SITE) return approachSite(brain, project, target);
        if (phase == Phase.WORKING) return workAndPlace(brain, project, target, key, expected, planned.blockId());

        return TaskResult.RUNNING;
    }

    private TaskResult collectMaterial(CitizenBrain brain, ConstructionProject project, ItemKey key,
                                       net.minecraft.resources.ResourceLocation itemId) {
        if (brain.distanceToSqr(project.stockpile()) > 12.25D) return travel(brain, project.stockpile());
        var container = service(brain).stockpile(project, brain.level()).orElse(null);
        if (container == null) {
            project.setState(ConstructionProjectState.BLOCKED, ConstructionBlocker.STOCKPILE_DESTROYED);
            return TaskResult.BLOCKED;
        }
        int capacity = brain.record().inventory().capacityFor(new ItemStack(BuiltInRegistries.ITEM.get(itemId)));
        ItemStack extracted = PhysicalInventoryOps.extract(container, key,
                Math.min(materialBatchSize(brain), capacity));
        if (extracted.isEmpty()) {
            project.setState(ConstructionProjectState.WAITING_FOR_MATERIALS, ConstructionBlocker.MISSING_MATERIAL);
            return TaskResult.BLOCKED;
        }
        int removed = extracted.getCount();
        brain.record().inventory().add(extracted);
        int carried = removed - extracted.getCount();
        if (!extracted.isEmpty()) PhysicalInventoryOps.insert(container, extracted);
        if (carried <= 0) return TaskResult.BLOCKED;
        service(brain).pickedUp(project, brain.record().citizenId(), itemId, carried);
        var economy = CivilisationServices.economy(brain.level().getServer());
        economy.endpoint(project.settlementId(), project.stockpileEndpoint())
                .ifPresent(endpoint -> economy.reconcile(endpoint, container));
        brain.markDirty();
        phase = Phase.TO_SITE;
        age = 0;
        phaseTicks = 0;
        resetInteractionPlanning(brain);
        showCargo(brain, project);
        moveToSite(brain, project);
        return TaskResult.RUNNING;
    }

    private TaskResult approachSite(CitizenBrain brain, ConstructionProject project, AssignedLocation target) {
        ItemKey needed = ItemKey.parse(project.blueprint().blocks().get(index).blockId().toString());
        if (brain.record().inventory().count(needed) <= 0) {
            phase = Phase.TO_STOCKPILE;
            phaseTicks = 0;
            resetInteractionPlanning(brain);
            brain.entity().clearVisualItem();
            move(brain, project.stockpile());
            return TaskResult.RUNNING;
        }
        if (!withinReachAndVisible(brain, project, target.pos())) {
            return travelToSite(brain, project);
        }
        brain.entity().getNavigation().stop();
        brain.lookAt(target);
        brain.entity().setTaskVisualState(CitizenVisualState.WORKING);
        phase = Phase.WORKING;
        age = 0;
        phaseTicks = 0;
        return TaskResult.RUNNING;
    }

    private TaskResult workAndPlace(CitizenBrain brain, ConstructionProject project, AssignedLocation target,
                                    ItemKey key, Block expected,
                                    net.minecraft.resources.ResourceLocation itemId) {
        if (!withinReachAndVisible(brain, project, target.pos())) {
            phase = Phase.TO_SITE;
            phaseTicks = 0;
            failCurrentCandidate(brain);
            if (++siteReplans >= MAX_SITE_REPLANS || !moveToSite(brain, project))
                return failAccess(brain, project);
            return TaskResult.RUNNING;
        }
        var existing = brain.level().getBlockState(target.pos());
        if (!existing.isAir() && !existing.canBeReplaced()) {
            project.setState(ConstructionProjectState.BLOCKED, ConstructionBlocker.WORLD_CHANGED);
            service(brain).failed(projectId, index, brain.record().citizenId());
            return TaskResult.BLOCKED;
        }
        brain.lookAt(target);
        brain.entity().setTaskVisualState(CitizenVisualState.WORKING);
        if (phaseTicks % 8 == 0) brain.entity().swing(InteractionHand.MAIN_HAND, true);
        if (++phaseTicks < workTicksFor(proficiency(brain))) return TaskResult.RUNNING;

        ItemStack held = brain.record().inventory().takeOne(key);
        if (held.isEmpty()) {
            phase = Phase.TO_STOCKPILE;
            phaseTicks = 0;
            brain.entity().clearVisualItem();
            move(brain, project.stockpile());
            return TaskResult.RUNNING;
        }
        var state = expected.defaultBlockState();
        if (!brain.level().setBlock(target.pos(), state, 3)) {
            brain.record().inventory().add(held);
            service(brain).failed(projectId, index, brain.record().citizenId());
            return TaskResult.BLOCKED;
        }
        brain.level().playSound(null, target.pos(), state.getSoundType().getPlaceSound(), SoundSource.BLOCKS,
                0.8F, 0.9F + brain.level().random.nextFloat() * 0.2F);
        brain.level().levelEvent(2001, target.pos(), Block.getId(state));
        service(brain).consumed(project, brain.record().citizenId(), itemId, 1);
        service(brain).placed(project, index, brain.record().citizenId());
        CivilisationServices.demography(brain.level().getServer())
                .recordConstructionPlacement(brain.record());
        CivilisationServices.knowledge(brain.level().getServer()).recordConstruction(brain.record(),
                itemId.getPath().contains("stone") || itemId.getPath().contains("cobble"));
        brain.markDirty();
        showCargo(brain, project);
        phase = Phase.COOLDOWN;
        phaseTicks = 0;
        return TaskResult.RUNNING;
    }

    private boolean withinReachAndVisible(CitizenBrain brain, ConstructionProject project, BlockPos target) {
        // Use exactly the same reach/sight test for candidate selection and placement.
        // A roof corner's centre may be hidden by the upper wall, while its exposed
        // top/edge is physically visible and within the citizen's reach.
        return visibleFrom(brain, project, brain.entity().getEyePosition(), target);
    }

    private TaskResult advance(CitizenBrain brain, ConstructionProject project) {
        if (project.state() == ConstructionProjectState.COMPLETE) {
            brain.entity().clearVisualItem();
            return TaskResult.COMPLETE;
        }
        var next = project.claim(brain.record().citizenId(), project.heldBy(brain.record().citizenId()).keySet());
        if (next.isEmpty()) {
            brain.entity().clearVisualItem();
            return TaskResult.COMPLETE;
        }
        releaseLeasedStance(brain);
        index = next.getAsInt();
        failedPositions.clear();
        phase = ownsCargoForTarget(brain, project) ? Phase.TO_SITE : Phase.TO_STOCKPILE;
        age = 0;
        phaseTicks = 0;
        resetInteractionPlanning(brain);
        // The construction claim reconciler runs independently of the citizen's
        // normal persistence cadence. Persist the new block index immediately so
        // a valid batch continuation is never mistaken for an abandoned claim.
        brain.persistNow();
        showCargo(brain, project);
        if (phase == Phase.TO_SITE) moveToSite(brain, project);
        else move(brain, project.stockpile());
        return TaskResult.RUNNING;
    }

    private void showCargo(CitizenBrain brain, ConstructionProject project) {
        var itemId = project.blueprint().blocks().get(index).blockId();
        ItemStack display = brain.record().inventory().first(ItemKey.parse(itemId.toString())).orElse(ItemStack.EMPTY);
        if (display.isEmpty()) brain.entity().clearVisualItem();
        else brain.entity().setVisualItem(display);
    }

    private boolean ownsCargoForTarget(CitizenBrain brain, ConstructionProject project) {
        var item = project.blueprint().blocks().get(index).blockId();
        return project.heldBy(brain.record().citizenId()).getOrDefault(item, 0) > 0
                && brain.record().inventory().count(ItemKey.parse(item.toString())) > 0;
    }

    private TaskResult travel(CitizenBrain brain, AssignedLocation target) {
        brain.entity().setTaskVisualState(CitizenVisualState.HAULING);
        if (++age > 1200) return TaskResult.PATH_FAILED;
        if (age % 40 == 0 && (brain.entity().position().distanceToSqr(last) < 0.04D
                || brain.entity().getNavigation().isDone())) {
            last = brain.entity().position();
            if (!move(brain, target)) return TaskResult.PATH_FAILED;
        }
        return TaskResult.RUNNING;
    }

    private TaskResult travelToSite(CitizenBrain brain, ConstructionProject project) {
        brain.entity().setTaskVisualState(CitizenVisualState.HAULING);
        if (++age > 1200) return failAccess(brain, project);
        if (age % 20 == 0) {
            BlockPos sample = brain.entity().blockPosition();
            boolean alternating = twoSamplesAgo != null && twoSamplesAgo.equals(sample)
                    && previousSample != null && !previousSample.equals(sample);
            oscillationSamples = alternating ? oscillationSamples + 1 : Math.max(0, oscillationSamples - 1);
            twoSamplesAgo = previousSample;
            previousSample = sample;

            Vec3 progressTarget = interactionPosition == null
                    ? Vec3.atCenterOf(target(project).pos())
                    : Vec3.atBottomCenterOf(interactionPosition);
            double distance = brain.entity().position().distanceToSqr(progressTarget);
            if (bestTargetDistance - distance >= 0.25D) {
                bestTargetDistance = distance;
                noProgressChecks = 0;
            } else noProgressChecks++;

            boolean pathEnded = brain.entity().getNavigation().isDone();
            if (pathEnded && !currentPathComplete && noProgressChecks == 0) {
                // Long routes can be clipped to the mob's follow range. Re-plan
                // from the closer endpoint without treating the candidate as bad.
                interactionPosition = null;
                if (!moveToSite(brain, project)) return failAccess(brain, project);
                return TaskResult.RUNNING;
            }
            boolean stalled = pathEnded || noProgressChecks >= 3
                    || oscillationSamples >= 2 || failedPositions.alternating();
            if (stalled) {
                failCurrentCandidate(brain);
                if (++siteReplans >= MAX_SITE_REPLANS || !moveToSite(brain, project))
                    return failAccess(brain, project);
            }
        }
        return TaskResult.RUNNING;
    }

    private boolean move(CitizenBrain brain, AssignedLocation target) {
        return brain.entity().getNavigation().moveTo(target.pos().getX() + 0.5D, target.pos().getY(),
                target.pos().getZ() + 0.5D, 1.0D);
    }

    /**
     * Prefer a path-tested standing position beside the placement.  A nearly complete
     * foundation is a walkable platform, but a path aimed at the empty block itself is
     * rejected by vanilla navigation.  This keeps placement physical while avoiding
     * remote reach-through or teleport fallbacks.
     */
    private boolean moveToSite(CitizenBrain brain, ConstructionProject project) {
        BlockPos placement = target(project).pos();
        // The outer terrain can be below the anchor on a hillside. Restricting
        // stance feet to the anchor height incorrectly rejects viable ground paths.
        int lowestFeetY = Math.max(brain.level().getMinBuildHeight(),
                project.anchor().pos().getY() - 2);
        // Include the level above completed roofs and legitimate access surfaces.
        int highestFeetY = Math.max(lowestFeetY, placement.getY() + 1);
        List<SiteCandidate> candidates = new ArrayList<>();
        int collisionRejected = 0;
        int supportRejected = 0;
        int geometryRejected = 0;
        int pathRejected = 0;
        var stanceService = CivilisationServices.constructionStances(brain.level().getServer());
        UUID builderId = brain.record().citizenId();
        long now = brain.level().getGameTime();
        var dimension = brain.level().dimension().location();
        for (BlockPos feet : ConstructionInteractionPlanner.candidates(placement, lowestFeetY, highestFeetY)) {
            if (!failedPositions.available(feet) || feet.equals(placement) || feet.above().equals(placement)) continue;
            if (!stanceService.available(projectId, dimension, feet, builderId, now)) continue;
            if (!passable(brain, feet) || !passable(brain, feet.above())) {
                collisionRejected++;
                continue;
            }
            var support = brain.level().getBlockState(feet.below());
            if (!support.isFaceSturdy(brain.level(), feet.below(), net.minecraft.core.Direction.UP)
                    && !support.is(Blocks.SCAFFOLDING)) {
                supportRejected++;
                continue;
            }
            Vec3 predictedEye = new Vec3(feet.getX() + 0.5D,
                    feet.getY() + brain.entity().getEyeHeight(), feet.getZ() + 0.5D);
            double reach = predictedEye.distanceToSqr(Vec3.atCenterOf(placement));
            if (!visibleFrom(brain, project, predictedEye, placement)) {
                geometryRejected++;
                continue;
            }
            Path path = brain.entity().getNavigation().createPath(feet, 0);
            if (path == null) {
                pathRejected++;
                continue;
            }
            boolean completePath = path.canReach();
            if (!path.canReach()) {
                // createPath is bounded by the mob's follow range. A path that
                // ends materially closer is a valid route segment, not proof the
                // final interaction position is unreachable. It will be re-planned
                // from its endpoint and must eventually produce a complete path.
                var end = path.getEndNode();
                if (end == null) {
                    pathRejected++;
                    continue;
                }
                BlockPos endpoint = new BlockPos(end.x, end.y, end.z);
                double before = brain.entity().blockPosition().distSqr(feet);
                double after = endpoint.distSqr(feet);
                if (path.getNodeCount() < 2 || after + 4.0D >= before) {
                    pathRejected++;
                    continue;
                }
            }
            double score = path.getNodeCount() * 4.0D + reach
                    + Math.abs(feet.getY() - placement.getY()) * 1.5D;
            // A completed floor/support inside the footprint is a legitimate work
            // surface and is preferred over a longer exterior route.
            if (project.plansWorldPosition(feet.below())) score -= 8.0D;
            if (support.is(Blocks.SCAFFOLDING)) score -= 2.0D;
            candidates.add(new SiteCandidate(feet.immutable(), path, score, completePath));
        }
        // A path ending closer is only a fallback. Never prefer it over a route
        // that vanilla navigation actually confirms can reach a valid stance.
        candidates.sort((a, b) -> ConstructionInteractionPlanner.compareCandidatePaths(
                a.completePath(), a.score(), b.completePath(), b.score()));
        // A rejected moveTo must not discard the rest of the reachable options.
        // Bound starts per replan to avoid expensive path churn on busy servers.
        int moveAttempts = 0;
        for (SiteCandidate candidate : candidates) {
            if (moveAttempts++ >= MAX_SITE_MOVE_ATTEMPTS) break;
            if (!stanceService.claim(projectId, dimension, candidate.position(), builderId, now)) continue;
            if (brain.entity().getNavigation().moveTo(candidate.path(), 1.0D)) {
                changeLeasedStance(brain, candidate.position());
                beginCandidate(brain, candidate.position(), candidate.completePath());
                return true;
            }
            if (!candidate.position().equals(leasedStance))
                stanceService.release(projectId, dimension, candidate.position(), builderId);
            failedPositions.failed(candidate.position());
        }

        BlockPos access = service(brain).accessTarget(project, index).pos();
        if (!failedPositions.available(access)) return false;
        Path fallback = brain.entity().getNavigation().createPath(access, 0);
        if (fallback != null && fallback.canReach()
                && stanceService.claim(projectId, dimension, access, builderId, now)) {
            if (brain.entity().getNavigation().moveTo(fallback, 1.0D)) {
                changeLeasedStance(brain, access);
                beginCandidate(brain, access, true);
                return true;
            }
            if (!access.equals(leasedStance)) stanceService.release(projectId, dimension, access, builderId);
        }
        CivilisationMod.LOGGER.debug("No construction interaction path for {} placement {} at {} "
                + "(collision={}, support={}, geometry={}, path={}, blacklisted={})",
                project.readableId(), index, placement.toShortString(), collisionRejected,
                supportRejected, geometryRejected, pathRejected, failedPositions.blockedCount());
        return false;
    }

    private boolean passable(CitizenBrain brain, BlockPos position) {
        return brain.level().getBlockState(position).getCollisionShape(brain.level(), position).isEmpty();
    }

    private void beginCandidate(CitizenBrain brain, BlockPos position, boolean completePath) {
        interactionPosition = position.immutable();
        currentPathComplete = completePath;
        failedPositions.selected(interactionPosition);
        last = brain.entity().position();
        bestTargetDistance = brain.entity().position().distanceToSqr(Vec3.atBottomCenterOf(position));
        noProgressChecks = 0;
    }

    private void failCurrentCandidate(CitizenBrain brain) {
        if (interactionPosition != null) failedPositions.failed(interactionPosition);
        releaseLeasedStance(brain);
        interactionPosition = null;
        noProgressChecks = 0;
    }

    private TaskResult failAccess(CitizenBrain brain, ConstructionProject project) {
        releaseLeasedStance(brain);
        service(brain).failed(projectId, index, brain.record().citizenId());
        if (project.needsAccess())
            project.setState(ConstructionProjectState.BLOCKED, ConstructionBlocker.ACCESS_REQUIRED);
        return TaskResult.PATH_FAILED;
    }

    private void resetInteractionPlanning(CitizenBrain brain) {
        releaseLeasedStance(brain);
        interactionPosition = null;
        previousSample = null;
        twoSamplesAgo = null;
        oscillationSamples = 0;
        noProgressChecks = 0;
        siteReplans = 0;
        bestTargetDistance = Double.MAX_VALUE;
        currentPathComplete = false;
    }

    /** Release old square only after the new standing square has been successfully reserved. */
    private void changeLeasedStance(CitizenBrain brain, BlockPos next) {
        if (leasedStance != null && !leasedStance.equals(next)) releaseLeasedStance(brain);
        leasedStance = next.immutable();
    }

    private void releaseLeasedStance(CitizenBrain brain) {
        if (leasedStance == null) return;
        CivilisationServices.constructionStances(brain.level().getServer()).release(
                projectId, brain.level().dimension().location(), leasedStance, brain.record().citizenId());
        leasedStance = null;
    }

    private record SiteCandidate(BlockPos position, Path path, double score, boolean completePath) {}

    private boolean visibleFrom(CitizenBrain brain, ConstructionProject project, Vec3 eye, BlockPos target) {
        for (Vec3 aim : ConstructionInteractionPlanner.aimPoints(target)) {
            if (eye.distanceToSqr(aim) > MAX_REACH_SQR) continue;
            BlockHitResult hit = brain.level().clip(new ClipContext(eye, aim,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, brain.entity()));
            if (validPlacementHit(brain, project, hit, target)) return true;
        }
        return false;
    }

    private boolean validPlacementHit(CitizenBrain brain, ConstructionProject project,
                                      BlockHitResult hit, BlockPos target) {
        if (hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(target)) return true;
        BlockPos support = hit.getBlockPos();
        // Permit a real support-face interaction only when the face hit by the
        // ray borders the target. This supports looking upward from inside while
        // preventing placement through a wall or several completed roof blocks.
        return support.relative(hit.getDirection()).equals(target)
                && brain.level().getBlockState(support).isSolidRender(brain.level(), support);
    }

    private AssignedLocation target(ConstructionProject project) {
        return new AssignedLocation(project.anchor().dimension(),
                project.blueprint().blocks().get(index).relativePos().resolve(project.anchor().pos()));
    }

    private ConstructionService service(CitizenBrain brain) {
        return CivilisationServices.construction(brain.level().getServer());
    }

    private ConstructionProject project(CitizenBrain brain) {
        return service(brain).data().project(projectId).orElse(null);
    }

    public static int workTicksFor(CareerLevel level) {
        return switch (level) {
            case NOVICE -> 40;
            case TRAINED -> 32;
            case EXPERIENCED -> 24;
            case MASTER -> 19;
        };
    }

    private CareerLevel proficiency(CitizenBrain brain) {
        return CareerLevel.fromExperience(brain.record().development()
                .experience(com.tyes.civilisation.settlement.WorkRole.BUILDER));
    }

    private int materialBatchSize(CitizenBrain brain) {
        return switch (proficiency(brain)) {
            case NOVICE -> 4;
            case TRAINED -> 6;
            case EXPERIENCED -> 8;
            case MASTER -> 10;
        };
    }

    @Override
    public boolean stillValid(CitizenBrain brain) {
        ConstructionProject project = project(brain);
        return project != null && project.owns(index, brain.record().citizenId()) && !project.complete(index);
    }

    @Override
    public void resume(CitizenBrain brain) {
        ConstructionProject project = project(brain);
        if (project != null) {
            phase = ownsCargoForTarget(brain, project) ? Phase.TO_SITE : Phase.TO_STOCKPILE;
            phaseTicks = 0;
            resetInteractionPlanning(brain);
            showCargo(brain, project);
            if (phase == Phase.TO_SITE) moveToSite(brain, project);
            else move(brain, project.stockpile());
        }
    }

    @Override
    public void cancel(CitizenBrain brain, TaskCancelReason reason) {
        releaseLeasedStance(brain);
        brain.entity().getNavigation().stop();
        if (reason == TaskCancelReason.STUCK) service(brain).failed(projectId, index, brain.record().citizenId());
        else service(brain).release(projectId, index, brain.record().citizenId());
        if (reason != TaskCancelReason.CRITICAL_NEED) brain.entity().clearVisualItem();
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("project", projectId);
        tag.putInt("index", index);
        tag.putString("phase", phase.name());
        tag.putInt("age", age);
        tag.putInt("phaseTicks", phaseTicks);
        return tag;
    }

    public static BuilderTask load(CompoundTag tag) {
        Phase loaded;
        try { loaded = Phase.valueOf(tag.getString("phase")); }
        catch (IllegalArgumentException ignored) { loaded = Phase.TO_SITE; }
        return new BuilderTask(tag.getUUID("project"), tag.getInt("index"), loaded,
                tag.getInt("age"), tag.getInt("phaseTicks"));
    }
}
