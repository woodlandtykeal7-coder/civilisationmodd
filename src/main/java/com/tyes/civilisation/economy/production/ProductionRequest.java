package com.tyes.civilisation.economy.production;

import com.tyes.civilisation.economy.item.ItemKey;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public final class ProductionRequest {
    private final UUID requestId;
    private final UUID settlementId;
    private final ItemKey output;
    private int desiredQuantity;
    private ProductionPriority priority;
    private final ProductionReason reason;
    private int completedQuantity;
    private ProductionState state = ProductionState.PLANNED;
    private ProductionBlocker blocker = ProductionBlocker.NONE;
    private ProductionMethod method = ProductionMethod.UNAVAILABLE;
    private ResourceLocation recipeId;
    private final Map<ItemKey, Integer> needs = new LinkedHashMap<>();
    private UUID claimedBy;

    public ProductionRequest(UUID requestId, UUID settlementId, ItemKey output, int desiredQuantity,
                             ProductionPriority priority, ProductionReason reason) {
        if (desiredQuantity <= 0) throw new IllegalArgumentException("Desired quantity must be positive");
        this.requestId = requestId;
        this.settlementId = settlementId;
        this.output = output;
        this.desiredQuantity = desiredQuantity;
        this.priority = priority;
        this.reason = reason;
    }

    public UUID requestId() { return requestId; }
    public UUID settlementId() { return settlementId; }
    public ItemKey output() { return output; }
    public int desiredQuantity() { return desiredQuantity; }
    public int completedQuantity() { return completedQuantity; }
    public int remainingQuantity() { return Math.max(0, desiredQuantity - completedQuantity); }
    public ProductionPriority priority() { return priority; }
    public ProductionReason reason() { return reason; }
    public ProductionState state() { return state; }
    public ProductionBlocker blocker() { return blocker; }
    public ProductionMethod method() { return method; }
    public Optional<ResourceLocation> recipeId() { return Optional.ofNullable(recipeId); }
    public Map<ItemKey, Integer> needs() { return Map.copyOf(needs); }
    public Optional<UUID> claimedBy() { return Optional.ofNullable(claimedBy); }

    public void plan(ProductionMethod method, ResourceLocation recipeId, Map<ItemKey, Integer> needs) {
        this.method = method;
        this.recipeId = recipeId;
        this.needs.clear();
        this.needs.putAll(needs);
        this.state = ProductionState.WAITING_FOR_INPUT;
        this.blocker = ProductionBlocker.NONE;
    }
    public void setState(ProductionState state) { this.state = state; if (state != ProductionState.BLOCKED) blocker = ProductionBlocker.NONE; }
    public void promote(ProductionPriority next) { if (next.ordinal() < priority.ordinal()) priority = next; }
    public void addDemand(int amount) {
        if (amount <= 0) return;
        desiredQuantity += amount;
        if (state == ProductionState.COMPLETE) state = ProductionState.PLANNED;
    }
    /** Idempotent planner update: require this much remaining work without adding the same deficit each pass. */
    public void ensureRemainingDemand(int amount) {
        if (amount <= remainingQuantity()) return;
        desiredQuantity = completedQuantity + amount;
        if (state == ProductionState.COMPLETE) state = ProductionState.PLANNED;
    }
    public void block(ProductionBlocker blocker) { this.state = ProductionState.BLOCKED; this.blocker = blocker; }
    public void claim(UUID citizenId) { claimedBy = citizenId; state = ProductionState.IN_PROGRESS; }
    public void releaseClaim() { claimedBy = null; if (state == ProductionState.IN_PROGRESS) state = ProductionState.READY; }
    public void awaitMachine() { claimedBy = null; state = ProductionState.IN_PROGRESS; blocker = ProductionBlocker.NONE; }
    public void complete(int amount) {
        completedQuantity = Math.min(desiredQuantity, completedQuantity + Math.max(0, amount));
        claimedBy = null;
        blocker = ProductionBlocker.NONE;
        state = remainingQuantity() == 0 ? ProductionState.COMPLETE : ProductionState.PLANNED;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("requestId", requestId); tag.putUUID("settlementId", settlementId);
        tag.put("output", output.save()); tag.putInt("desired", desiredQuantity); tag.putInt("completed", completedQuantity);
        tag.putString("priority", priority.name()); tag.putString("reason", reason.name());
        tag.putString("state", state.name()); tag.putString("blocker", blocker.name()); tag.putString("method", method.name());
        if (recipeId != null) tag.putString("recipeId", recipeId.toString());
        if (claimedBy != null) tag.putUUID("claimedBy", claimedBy);
        ListTag needsTag = new ListTag();
        needs.forEach((key, amount) -> { CompoundTag entry = new CompoundTag(); entry.put("item", key.save()); entry.putInt("amount", amount); needsTag.add(entry); });
        tag.put("needs", needsTag);
        return tag;
    }

    public static ProductionRequest load(CompoundTag tag) {
        ProductionRequest request = new ProductionRequest(tag.getUUID("requestId"), tag.getUUID("settlementId"),
                ItemKey.load(tag.getCompound("output")), tag.getInt("desired"),
                ProductionPriority.valueOf(tag.getString("priority")), ProductionReason.valueOf(tag.getString("reason")));
        request.completedQuantity = Math.max(0, Math.min(request.desiredQuantity, tag.getInt("completed")));
        request.state = ProductionState.valueOf(tag.getString("state"));
        request.blocker = ProductionBlocker.valueOf(tag.getString("blocker"));
        request.method = ProductionMethod.valueOf(tag.getString("method"));
        request.recipeId = ResourceLocation.tryParse(tag.getString("recipeId"));
        request.claimedBy = tag.hasUUID("claimedBy") ? tag.getUUID("claimedBy") : null;
        if (request.state == ProductionState.COMPLETE) request.blocker = ProductionBlocker.NONE;
        ListTag needs = tag.getList("needs", Tag.TAG_COMPOUND);
        for (int i = 0; i < needs.size(); i++) request.needs.put(ItemKey.load(needs.getCompound(i).getCompound("item")), needs.getCompound(i).getInt("amount"));
        return request;
    }
}
