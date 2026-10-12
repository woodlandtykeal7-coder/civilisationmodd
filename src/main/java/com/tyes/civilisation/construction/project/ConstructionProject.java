package com.tyes.civilisation.construction.project;

import com.tyes.civilisation.citizen.AssignedLocation;
import com.tyes.civilisation.construction.blueprint.ResolvedBlueprint;
import com.tyes.civilisation.construction.material.MaterialPalette;
import com.tyes.civilisation.economy.inventory.InventoryEndpointId;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;

/** Persistent coordination only; all resources remain physical ItemStacks. */
public final class ConstructionProject {
    private final UUID id, settlementId; private final String readableId;
    private final AssignedLocation anchor, stockpile; private final InventoryEndpointId stockpileEndpoint;
    private final ResolvedBlueprint blueprint; private final MaterialPalette palette;
    private final BitSet completed=new BitSet(); private final Map<Integer,UUID> claims=new HashMap<>(); private final Set<Long> footprint=new HashSet<>();
    private final Map<Integer,Integer> failures=new HashMap<>();
    private final Map<UUID,Map<ResourceLocation,Integer>> heldCargo=new LinkedHashMap<>();
    private final Set<AssignedLocation> temporaryAccess=new LinkedHashSet<>();
    // Computed from each project's immutable blueprint; never changes saved block indices.
    private final List<Integer> placementOrder;
    private ConstructionProjectState state=ConstructionProjectState.PLANNED; private ConstructionBlocker blocker=ConstructionBlocker.NONE;
    private ConstructionPriority priority=ConstructionPriority.NORMAL; private String priorityReason="normal construction"; private long priorityChangedTick;
    private UUID registeredBuildingId;
    public ConstructionProject(UUID id,String readableId,UUID settlementId,AssignedLocation anchor,AssignedLocation stockpile,InventoryEndpointId endpoint,ResolvedBlueprint blueprint,MaterialPalette palette){this.id=id;this.readableId=readableId;this.settlementId=settlementId;this.anchor=anchor;this.stockpile=stockpile;this.stockpileEndpoint=endpoint;this.blueprint=blueprint;this.palette=palette;blueprint.blocks().forEach(b->footprint.add(b.relativePos().resolve(BlockPos.ZERO).asLong()));placementOrder=buildPlacementOrder(blueprint);}
    public UUID projectId(){return id;} public String readableId(){return readableId;} public UUID settlementId(){return settlementId;} public AssignedLocation anchor(){return anchor;} public AssignedLocation stockpile(){return stockpile;} public InventoryEndpointId stockpileEndpoint(){return stockpileEndpoint;} public ResolvedBlueprint blueprint(){return blueprint;} public MaterialPalette palette(){return palette;} public ConstructionProjectState state(){return state;} public ConstructionBlocker blocker(){return blocker;} public int completedPlacements(){return completed.cardinality();} public int totalPlacements(){return blueprint.blocks().size();} public boolean complete(int i){return completed.get(i);} public boolean plansWorldPosition(BlockPos pos){return footprint.contains(pos.subtract(anchor.pos()).asLong());} public Optional<UUID> registeredBuildingId(){return Optional.ofNullable(registeredBuildingId);} public void registered(UUID id){if(registeredBuildingId==null)registeredBuildingId=id;} public void setState(ConstructionProjectState s,ConstructionBlocker b){state=s;blocker=b==null?ConstructionBlocker.NONE:b;}
    public ConstructionPriority priority(){return priority;} public String priorityReason(){return priorityReason;} public long priorityChangedTick(){return priorityChangedTick;}
    public boolean updatePriority(ConstructionPriority next,String reason,long now){if(next==null)return false;if(next==priority){priorityReason=reason;return false;}if(next.outranks(priority)||now-priorityChangedTick>=2400L){priority=next;priorityReason=reason;priorityChangedTick=now;return true;}return false;}
    public Map<Integer,UUID> claims(){return Map.copyOf(claims);} public Map<Integer,Integer> failures(){return Map.copyOf(failures);}
    /**
     * All foundations/walls finish before the roof. Starter-house roof blocks
     * are claimed from the back (z=8) toward the entry at z=0, leaving the
     * entrance-side work until last. This is claim order only: blueprint indices,
     * inventories, completed bits and saved-world data are unchanged.
     */
    private static List<Integer> buildPlacementOrder(ResolvedBlueprint blueprint) {
        List<Integer> order = new ArrayList<>();
        for (int i=0; i<blueprint.blocks().size(); i++) order.add(i);
        boolean starterHouse = "civilisation".equals(blueprint.buildingType().getNamespace())
                && "starter_house".equals(blueprint.buildingType().getPath());
        order.sort(Comparator.comparingInt((Integer i)->blueprint.blocks().get(i).relativePos().y())
                .thenComparingInt(i -> {
                    var pos = blueprint.blocks().get(i).relativePos();
                    return starterHouse && pos.y() == 4 ? -pos.z() : 0;
                }).thenComparingInt(Integer::intValue));
        return List.copyOf(order);
    }
    public OptionalInt claim(UUID citizen,Set<ResourceLocation> preferred){int activeLayer=Integer.MAX_VALUE;for(int i=0;i<totalPlacements();i++)if(!completed.get(i))activeLayer=Math.min(activeLayer,blueprint.blocks().get(i).relativePos().y());for(int pass=0;pass<2;pass++)for(int i:placementOrder){if(completed.get(i)||blueprint.blocks().get(i).relativePos().y()!=activeLayer||claims.containsKey(i)||failures.getOrDefault(i,0)>=3)continue;ResourceLocation item=blueprint.blocks().get(i).blockId();if(pass==0&&!preferred.contains(item))continue;if(heldCargo.getOrDefault(citizen,Map.of()).getOrDefault(item,0)==0&&held(item)>=incomplete(item))continue;claims.put(i,citizen);state=ConstructionProjectState.BUILDING;if(!needsAccess())blocker=ConstructionBlocker.NONE;return OptionalInt.of(i);}return OptionalInt.empty();}
    private int incomplete(ResourceLocation item){int count=0;for(int i=0;i<totalPlacements();i++)if(!completed.get(i)&&blueprint.blocks().get(i).blockId().equals(item))count++;return count;}
    public void release(int i,UUID c){if(c.equals(claims.get(i)))claims.remove(i);} public void failed(int i,UUID c){release(i,c);failures.merge(i,1,Integer::sum);if(needsAccess())setState(ConstructionProjectState.BLOCKED,ConstructionBlocker.ACCESS_REQUIRED);} public boolean needsAccess(){return failures.values().stream().anyMatch(n->n>=3);} public void clearAccessFailures(){failures.entrySet().removeIf(e->e.getValue()>=3);if(blocker==ConstructionBlocker.ACCESS_REQUIRED)setState(ConstructionProjectState.READY,ConstructionBlocker.NONE);} public void releaseAll(UUID c){claims.values().removeIf(c::equals);} public boolean owns(int i,UUID c){return c.equals(claims.get(i));} public void markComplete(int i,UUID c){if(owns(i,c)){completed.set(i);claims.remove(i);failures.remove(i);}} public void reopen(int i){completed.clear(i);claims.remove(i);failures.remove(i);setState(ConstructionProjectState.BLOCKED,ConstructionBlocker.WORLD_CHANGED);}
    public void cargoPickedUp(UUID c,ResourceLocation item,int n){if(n>0)heldCargo.computeIfAbsent(c,k->new LinkedHashMap<>()).merge(item,n,Integer::sum);} public void cargoConsumed(UUID c,ResourceLocation item,int n){var m=heldCargo.get(c);if(m==null)return;m.computeIfPresent(item,(k,v)->v<=n?null:v-n);if(m.isEmpty())heldCargo.remove(c);} public int held(ResourceLocation item){return heldCargo.values().stream().mapToInt(m->m.getOrDefault(item,0)).sum();} public int heldTotal(){return heldCargo.values().stream().flatMap(m->m.values().stream()).mapToInt(Integer::intValue).sum();} public Map<ResourceLocation,Integer> heldBy(UUID c){return Map.copyOf(heldCargo.getOrDefault(c,Map.of()));} public Set<UUID> cargoOwners(){return Set.copyOf(heldCargo.keySet());} public Map<ResourceLocation,Integer> heldTotals(){Map<ResourceLocation,Integer>r=new LinkedHashMap<>();heldCargo.values().forEach(m->m.forEach((k,v)->r.merge(k,v,Integer::sum)));return Map.copyOf(r);} public void addTemporaryAccess(AssignedLocation p){temporaryAccess.add(p);} public Set<AssignedLocation> temporaryAccess(){return Set.copyOf(temporaryAccess);} public void clearTemporaryAccess(){temporaryAccess.clear();}
    public CompoundTag save(){CompoundTag t=new CompoundTag();t.putUUID("id",id);t.putString("readable",readableId);t.putUUID("settlement",settlementId);t.put("anchor",anchor.save());t.put("stockpile",stockpile.save());t.putUUID("endpoint",stockpileEndpoint.value());t.put("blueprint",blueprint.save());t.put("palette",palette.save());t.putString("state",state.name());t.putString("blocker",blocker.name());t.putString("priority",priority.name());t.putString("priorityReason",priorityReason);t.putLong("priorityChanged",priorityChangedTick);t.putByteArray("completed",completed.toByteArray());if(registeredBuildingId!=null)t.putUUID("building",registeredBuildingId);ListTag failed=new ListTag();failures.forEach((i,n)->{CompoundTag e=new CompoundTag();e.putInt("index",i);e.putInt("count",n);failed.add(e);});t.put("failures",failed);ListTag cargo=new ListTag();heldCargo.forEach((c,m)->m.forEach((item,n)->{CompoundTag e=new CompoundTag();e.putUUID("citizen",c);e.putString("item",item.toString());e.putInt("count",n);cargo.add(e);}));t.put("cargo",cargo);ListTag access=new ListTag();temporaryAccess.forEach(a->access.add(a.save()));t.put("access",access);return t;}
    public static ConstructionProject load(CompoundTag t){var p=new ConstructionProject(t.getUUID("id"),t.getString("readable"),t.getUUID("settlement"),AssignedLocation.load(t.getCompound("anchor")).orElseThrow(),AssignedLocation.load(t.getCompound("stockpile")).orElseThrow(),new InventoryEndpointId(t.getUUID("endpoint")),ResolvedBlueprint.load(t.getCompound("blueprint")),MaterialPalette.load(t.getCompound("palette")));p.state=ConstructionProjectState.valueOf(t.getString("state"));p.blocker=ConstructionBlocker.valueOf(t.getString("blocker"));if(t.contains("priority")){try{p.priority=ConstructionPriority.valueOf(t.getString("priority"));}catch(IllegalArgumentException ignored){p.priority=ConstructionPriority.NORMAL;}}p.priorityReason=t.contains("priorityReason")?t.getString("priorityReason"):"migrated construction project";p.priorityChangedTick=t.getLong("priorityChanged");p.completed.or(BitSet.valueOf(t.getByteArray("completed")));if(t.hasUUID("building"))p.registeredBuildingId=t.getUUID("building");ListTag f=t.getList("failures",Tag.TAG_COMPOUND);for(int i=0;i<f.size();i++){var e=f.getCompound(i);p.failures.put(e.getInt("index"),e.getInt("count"));}ListTag c=t.getList("cargo",Tag.TAG_COMPOUND);for(int i=0;i<c.size();i++){var e=c.getCompound(i);p.cargoPickedUp(e.getUUID("citizen"),ResourceLocation.parse(e.getString("item")),e.getInt("count"));}ListTag a=t.getList("access",Tag.TAG_COMPOUND);for(int i=0;i<a.size();i++)AssignedLocation.load(a.getCompound(i)).ifPresent(p.temporaryAccess::add);return p;}
}
