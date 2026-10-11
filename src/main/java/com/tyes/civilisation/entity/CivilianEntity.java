package com.tyes.civilisation.entity;

import com.tyes.civilisation.CivilisationMod;
import com.tyes.civilisation.server.CitizenBindingService;
import com.tyes.civilisation.brain.CitizenBrain;
import com.tyes.civilisation.brain.TaskCancelReason;
import com.tyes.civilisation.persistence.CitizenSavedData;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.server.level.ServerPlayer;
import com.tyes.civilisation.network.DashboardNetwork;
import net.minecraft.world.level.Level;
import com.tyes.civilisation.citizen.identity.CitizenAppearanceProfile;
import com.tyes.civilisation.citizen.identity.CitizenAppearanceService;

/** The loaded physical body of a persistent {@code CitizenRecord}. */
public final class CivilianEntity extends PathfinderMob {
    private static final String CITIZEN_ID_TAG = "CivilisationCitizenId";
    private static final EntityDataAccessor<Byte> VISUAL_STATE = SynchedEntityData.defineId(
            CivilianEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<ItemStack> VISUAL_ITEM = SynchedEntityData.defineId(
            CivilianEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Integer> APPEARANCE_PROFILE = SynchedEntityData.defineId(
            CivilianEntity.class, EntityDataSerializers.INT);
    private static final int HURT_VISUAL_TICKS = 10;

    @Nullable
    private UUID citizenId;
    @Nullable
    private CitizenBrain civilisationBrain;
    private CitizenVisualState taskVisualState = CitizenVisualState.IDLE;
    private int recoveringTicks;

    public CivilianEntity(EntityType<? extends CivilianEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(VISUAL_STATE, (byte)CitizenVisualState.IDLE.ordinal());
        builder.define(VISUAL_ITEM, ItemStack.EMPTY);
        builder.define(APPEARANCE_PROFILE, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    public Optional<UUID> citizenId() {
        return Optional.ofNullable(citizenId);
    }

    public void assignCitizenId(UUID citizenId) {
        if (this.citizenId != null && !this.citizenId.equals(citizenId)) {
            throw new IllegalStateException("A civilian body cannot be reassigned to another citizen");
        }
        this.citizenId = citizenId;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        if (civilisationBrain != null) civilisationBrain.persistNow();
        super.addAdditionalSaveData(tag);
        if (citizenId != null) {
            tag.putUUID(CITIZEN_ID_TAG, citizenId);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        citizenId = tag.hasUUID(CITIZEN_ID_TAG) ? tag.getUUID(CITIZEN_ID_TAG) : null;
        civilisationBrain = null;
        if (citizenId == null) {
            CivilisationMod.LOGGER.warn("Loaded civilian entity {} without a citizen ID", getUUID());
        }
    }

    @Override
    public void die(DamageSource source) {
        if (civilisationBrain != null) civilisationBrain.cancel(TaskCancelReason.CITIZEN_DIED);
        super.die(source);
        if (level() instanceof ServerLevel serverLevel) {
            CitizenBindingService.onDeath(serverLevel, this);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && !isDeadOrDying()) {
            recoveringTicks = HURT_VISUAL_TICKS;
            syncVisualState(CitizenVisualState.RECOVERING);
        }
        return hurt;
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel serverLevel && citizenId != null && !isDeadOrDying()) {
            if (civilisationBrain == null) {
                CitizenSavedData data = CitizenSavedData.get(serverLevel.getServer());
                data.registry().find(citizenId).ifPresent(record ->
                        civilisationBrain = new CitizenBrain(this, record, data));
            }
            if (civilisationBrain != null) civilisationBrain.tick();
            if (recoveringTicks > 0 && --recoveringTicks == 0) syncVisualState(taskVisualState);
        }
    }

    public CitizenVisualState visualState() {
        return CitizenVisualState.fromNetwork(entityData.get(VISUAL_STATE));
    }

    public ItemStack visualItem() {
        return entityData.get(VISUAL_ITEM);
    }

    public CitizenAppearanceService.Definition appearanceDefinition() {
        return CitizenAppearanceService.byNetworkId(entityData.get(APPEARANCE_PROFILE));
    }

    /** Set by the server binding service; synced once to tracking clients with normal entity data. */
    public void setAppearance(CitizenAppearanceProfile appearance) {
        if (!level().isClientSide) {
            int encoded = CitizenAppearanceService.networkId(appearance);
            if (entityData.get(APPEARANCE_PROFILE) != encoded) entityData.set(APPEARANCE_PROFILE, encoded);
        }
    }

    /** Called by the authoritative brain; this state never controls task outcomes. */
    public void setTaskVisualState(CitizenVisualState state) {
        if (level().isClientSide) return;
        taskVisualState = state;
        if (recoveringTicks == 0) syncVisualState(state);
    }

    /** A synced display-only copy. It is never placed in entity equipment or dropped. */
    public void setVisualItem(ItemStack stack) {
        if (!level().isClientSide) entityData.set(VISUAL_ITEM, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
    }

    public void clearVisualItem() {
        if (!level().isClientSide) entityData.set(VISUAL_ITEM, ItemStack.EMPTY);
    }

    private void syncVisualState(CitizenVisualState state) {
        byte encoded = (byte)state.ordinal();
        if (entityData.get(VISUAL_STATE) != encoded) entityData.set(VISUAL_STATE, encoded);
    }

    public Optional<CitizenBrain> civilisationBrain() {
        return Optional.ofNullable(civilisationBrain);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || citizenId == null) return super.mobInteract(player, hand);
        if (player instanceof ServerPlayer serverPlayer) {
            CitizenSavedData.get(serverPlayer.server).registry().find(citizenId)
                    .ifPresent(record -> DashboardNetwork.openCitizen(serverPlayer, record));
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }
}
