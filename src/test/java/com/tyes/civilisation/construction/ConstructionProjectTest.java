package com.tyes.civilisation.construction;
import com.tyes.civilisation.citizen.AssignedLocation;import com.tyes.civilisation.construction.blueprint.*;import com.tyes.civilisation.construction.material.*;import com.tyes.civilisation.construction.project.*;import com.tyes.civilisation.construction.style.ArchitectureStyleId;import com.tyes.civilisation.economy.inventory.InventoryEndpointId;import java.util.*;import net.minecraft.core.BlockPos;import net.minecraft.resources.ResourceLocation;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
final class ConstructionProjectTest {
 private ConstructionProject project(){var stone=ResourceLocation.parse("minecraft:cobblestone");var bp=new ResolvedBlueprint(UUID.randomUUID(),ResourceLocation.parse("civilisation:starter_house"),new ArchitectureStyleId(ResourceLocation.parse("civilisation:temperate_timber")),1,List.of(new PlannedBlock(new RelativeBlockPos(0,0,0),stone,true),new PlannedBlock(new RelativeBlockPos(1,0,0),stone,true)));var loc=new AssignedLocation(ResourceLocation.parse("minecraft:overworld"),BlockPos.ZERO);return new ConstructionProject(UUID.randomUUID(),"P-0001",UUID.randomUUID(),loc,loc,new InventoryEndpointId(UUID.randomUUID()),bp,new MaterialPalette(Map.of(MaterialRole.FOUNDATION,stone)));}
 @Test void twoBuildersNeverClaimSameBlock(){var p=project();UUID a=UUID.randomUUID(),b=UUID.randomUUID();int x=p.claim(a,Set.of()).orElseThrow(),y=p.claim(b,Set.of()).orElseThrow();assertNotEquals(x,y);assertTrue(p.owns(x,a));assertFalse(p.owns(x,b));}
 @Test void completedPlacementCannotBeClaimedAgainAndPersists(){var p=project();UUID a=UUID.randomUUID();int x=p.claim(a,Set.of()).orElseThrow();p.markComplete(x,a);var loaded=ConstructionProject.load(p.save());assertTrue(loaded.complete(x));assertEquals(1,loaded.completedPlacements());assertNotEquals(x,loaded.claim(UUID.randomUUID(),Set.of()).orElseThrow());}
 @Test void duplicateCommitDoesNotAdvanceProgress(){var p=project();UUID a=UUID.randomUUID();int x=p.claim(a,Set.of()).orElseThrow();p.markComplete(x,a);p.markComplete(x,a);assertEquals(1,p.completedPlacements());}
 @Test void builderCargoIsPersistentAndPrioritised(){var p=project();UUID a=UUID.randomUUID();ResourceLocation stone=ResourceLocation.parse("minecraft:cobblestone");p.cargoPickedUp(a,stone,2);var loaded=ConstructionProject.load(p.save());assertEquals(2,loaded.held(stone));assertEquals(2,loaded.heldTotal());assertEquals(0,loaded.claim(a,loaded.heldBy(a).keySet()).orElseThrow());loaded.cargoConsumed(a,stone,1);assertEquals(1,loaded.held(stone));}
 @Test void boundedPathFailuresPersistAndRequestAccess(){var p=project();UUID a=UUID.randomUUID();for(int attempt=0;attempt<3;attempt++){int index=p.claim(a,Set.of()).orElseThrow();assertEquals(0,index);p.failed(index,a);}assertTrue(p.needsAccess());assertEquals(ConstructionBlocker.ACCESS_REQUIRED,p.blocker());var loaded=ConstructionProject.load(p.save());assertTrue(loaded.needsAccess());assertEquals(ConstructionBlocker.ACCESS_REQUIRED,loaded.blocker());loaded.clearAccessFailures();assertFalse(loaded.needsAccess());}
 @Test void failedOrCancelledClaimKeepsPhysicalCargoAssigned(){var p=project();UUID a=UUID.randomUUID();ResourceLocation stone=ResourceLocation.parse("minecraft:cobblestone");int index=p.claim(a,Set.of()).orElseThrow();p.cargoPickedUp(a,stone,2);p.failed(index,a);assertEquals(2,p.held(stone));index=p.claim(a,Set.of(stone)).orElseThrow();p.release(index,a);assertEquals(2,p.held(stone));}
 @Test void twoBuildersCargoIsCountedOnceAndSurvivesReload(){var p=project();UUID a=UUID.randomUUID(),b=UUID.randomUUID();ResourceLocation stone=ResourceLocation.parse("minecraft:cobblestone");p.cargoPickedUp(a,stone,1);p.cargoPickedUp(b,stone,1);assertEquals(2,p.held(stone));assertEquals(1,p.heldBy(a).get(stone));assertEquals(1,p.heldBy(b).get(stone));var loaded=ConstructionProject.load(p.save());assertEquals(2,loaded.held(stone));assertEquals(Set.of(a,b),loaded.cargoOwners());}
 @Test void persistentRegistrationMarkerIsIdempotent(){var p=project();UUID first=UUID.randomUUID(),second=UUID.randomUUID();p.registered(first);p.registered(second);assertEquals(first,p.registeredBuildingId().orElseThrow());assertEquals(first,ConstructionProject.load(p.save()).registeredBuildingId().orElseThrow());}
 @Test void starterHouseFinishesEveryWallLayerBeforeBackToFrontRoof(){
  var wood=ResourceLocation.parse("minecraft:oak_planks");
  // Intentionally shuffle block list: the immutable blueprint index must NOT
  // be changed by the claim-order policy.
  var blocks=List.of(
      new PlannedBlock(new RelativeBlockPos(3,4,0),wood,true),  // front/door roof
      new PlannedBlock(new RelativeBlockPos(0,4,8),wood,true),  // rear roof corner
      new PlannedBlock(new RelativeBlockPos(0,1,0),wood,true),  // wall
      new PlannedBlock(new RelativeBlockPos(0,2,0),wood,true),  // wall
      new PlannedBlock(new RelativeBlockPos(0,3,0),wood,true),  // wall top
      new PlannedBlock(new RelativeBlockPos(3,4,6),wood,true),
      new PlannedBlock(new RelativeBlockPos(3,4,8),wood,true));
  var blueprint=new ResolvedBlueprint(UUID.randomUUID(),ResourceLocation.parse("civilisation:starter_house"),
      new ArchitectureStyleId(ResourceLocation.parse("civilisation:temperate_timber")),1,blocks);
  var location=new AssignedLocation(ResourceLocation.parse("minecraft:overworld"),BlockPos.ZERO);
  var p=new ConstructionProject(UUID.randomUUID(),"P-ROOF",UUID.randomUUID(),location,location,
      new InventoryEndpointId(UUID.randomUUID()),blueprint,new MaterialPalette(Map.of(MaterialRole.ROOF,wood)));
  UUID builder=UUID.randomUUID();
  for(int expected:new int[]{2,3,4,1,6,5,0}){
      int claimed=p.claim(builder,Set.of()).orElseThrow();
      assertEquals(expected,claimed,"Walls first; roof progresses rear to entrance");
      p.markComplete(claimed,builder);
  }
  assertEquals(7,p.completedPlacements());
 }
 @Test void roofOrderSurvivesReloadWithoutReassigningClaimedBlocks(){
  var wood=ResourceLocation.parse("minecraft:oak_planks");
  var blocks=List.of(
      new PlannedBlock(new RelativeBlockPos(2,4,0),wood,true),
      new PlannedBlock(new RelativeBlockPos(2,4,8),wood,true),
      new PlannedBlock(new RelativeBlockPos(2,4,7),wood,true));
  var bp=new ResolvedBlueprint(UUID.randomUUID(),ResourceLocation.parse("civilisation:starter_house"),
      new ArchitectureStyleId(ResourceLocation.parse("civilisation:temperate_timber")),1,blocks);
  var loc=new AssignedLocation(ResourceLocation.parse("minecraft:overworld"),BlockPos.ZERO);
  var p=new ConstructionProject(UUID.randomUUID(),"P-ROOF",UUID.randomUUID(),loc,loc,
      new InventoryEndpointId(UUID.randomUUID()),bp,new MaterialPalette(Map.of(MaterialRole.ROOF,wood)));
  UUID first=UUID.randomUUID();
  assertEquals(1,p.claim(first,Set.of()).orElseThrow());
  p.markComplete(1,first);
  var loaded=ConstructionProject.load(p.save());
  assertEquals(1,loaded.completedPlacements());
  assertEquals(2,loaded.claim(UUID.randomUUID(),Set.of()).orElseThrow());
  assertFalse(loaded.complete(0));
 }
 @Test void laterLayersCannotSealOffIncompleteFoundation(){var stone=ResourceLocation.parse("minecraft:cobblestone");var bp=new ResolvedBlueprint(UUID.randomUUID(),ResourceLocation.parse("civilisation:starter_house"),new ArchitectureStyleId(ResourceLocation.parse("civilisation:temperate_timber")),1,List.of(new PlannedBlock(new RelativeBlockPos(0,0,0),stone,true),new PlannedBlock(new RelativeBlockPos(0,1,0),stone,true)));var loc=new AssignedLocation(ResourceLocation.parse("minecraft:overworld"),BlockPos.ZERO);var p=new ConstructionProject(UUID.randomUUID(),"P-0002",UUID.randomUUID(),loc,loc,new InventoryEndpointId(UUID.randomUUID()),bp,new MaterialPalette(Map.of(MaterialRole.FOUNDATION,stone)));UUID a=UUID.randomUUID();assertEquals(0,p.claim(a,Set.of()).orElseThrow());p.release(0,a);assertEquals(0,p.claim(a,Set.of()).orElseThrow());p.markComplete(0,a);assertEquals(1,p.claim(a,Set.of()).orElseThrow());}
 @Test void priorityPersistsAndDowngradeUsesHysteresis(){var p=project();assertTrue(p.updatePriority(ConstructionPriority.HIGH,"homeless household",100));assertFalse(p.updatePriority(ConstructionPriority.NORMAL,"pressure eased",200));assertEquals(ConstructionPriority.HIGH,p.priority());assertTrue(p.updatePriority(ConstructionPriority.NORMAL,"pressure remained lower",2600));var loaded=ConstructionProject.load(p.save());assertEquals(ConstructionPriority.NORMAL,loaded.priority());assertEquals("pressure remained lower",loaded.priorityReason());assertEquals(2600,loaded.priorityChangedTick());}
}
