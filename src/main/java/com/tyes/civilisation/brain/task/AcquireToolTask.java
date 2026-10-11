package com.tyes.civilisation.brain.task;

import com.tyes.civilisation.brain.*;
import com.tyes.civilisation.economy.inventory.InventoryEndpoint;
import com.tyes.civilisation.economy.inventory.PhysicalInventoryOps;
import com.tyes.civilisation.economy.reservation.ResourceReservation;
import com.tyes.civilisation.entity.CitizenVisualState;
import com.tyes.civilisation.server.CivilisationServices;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/** Checks one physically stored tool out to the citizen's persistent personal inventory. */
public final class AcquireToolTask implements CitizenTask {
    public static final String TYPE = "economy_acquire_tool";
    private final UUID settlementId, reservationId;
    private int ticks, pathAge;
    public AcquireToolTask(ResourceReservation r) { this(r.settlementId(), r.reservationId(), 0, 0); }
    private AcquireToolTask(UUID settlementId, UUID reservationId, int ticks, int pathAge) { this.settlementId=settlementId;this.reservationId=reservationId;this.ticks=ticks;this.pathAge=pathAge; }
    @Override public String type(){return TYPE;} @Override public CitizenActivityState activity(){return CitizenActivityState.WORKING;}
    @Override public TaskStartResult start(CitizenBrain brain){ ResourceReservation r=reservation(brain); InventoryEndpoint e=r==null?null:endpoint(brain,r); if(e==null)return TaskStartResult.INVALID; return move(brain,e)?TaskStartResult.STARTED:TaskStartResult.PATH_FAILED; }
    @Override public TaskResult tick(CitizenBrain brain){
        ResourceReservation r=reservation(brain); InventoryEndpoint e=r==null?null:endpoint(brain,r); if(e==null)return TaskResult.BLOCKED;
        if(brain.distanceToSqr(e.location())>12.25D){ brain.entity().setTaskVisualState(CitizenVisualState.WALKING); if(!e.location().isIn(brain.level())||!brain.level().isLoaded(e.location().pos()))return TaskResult.WAITING; if(++pathAge>1200)return TaskResult.PATH_FAILED; if(pathAge%60==0&&!move(brain,e))return TaskResult.PATH_FAILED; return TaskResult.RUNNING; }
        Container c=e.resolve(brain.level()).orElse(null);if(c==null)return TaskResult.BLOCKED;brain.lookAt(e.location());
        if(++ticks==1)brain.openChestVisual(e.location());if(ticks<15)return TaskResult.RUNNING;
        ItemStack tool=PhysicalInventoryOps.extract(c,r.item(),1);brain.closeChestVisual(e.location());
        if(tool.isEmpty())return TaskResult.BLOCKED;if(!brain.record().inventory().add(tool)||!tool.isEmpty())return TaskResult.BLOCKED;
        var economy=CivilisationServices.economy(brain.level().getServer());economy.reservations().release(settlementId,reservationId);economy.reconcile(e,c);brain.markDirty();return TaskResult.COMPLETE;
    }
    private boolean move(CitizenBrain b,InventoryEndpoint e){var p=e.location().pos();return b.entity().getNavigation().moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,2,1D);}
    private ResourceReservation reservation(CitizenBrain b){return CivilisationServices.economy(b.level().getServer()).reservation(settlementId,reservationId).orElse(null);}
    private InventoryEndpoint endpoint(CitizenBrain b,ResourceReservation r){return CivilisationServices.economy(b.level().getServer()).endpoint(settlementId,r.source()).orElse(null);}
    @Override public boolean stillValid(CitizenBrain b){return reservation(b)!=null&&!b.record().inventory().has(ItemTags.AXES);}
    @Override public void cancel(CitizenBrain b,TaskCancelReason reason){b.entity().getNavigation().stop();CivilisationServices.economy(b.level().getServer()).reservations().release(settlementId,reservationId);}
    @Override public CompoundTag save(){CompoundTag t=new CompoundTag();t.putUUID("settlement",settlementId);t.putUUID("reservation",reservationId);t.putInt("ticks",ticks);t.putInt("pathAge",pathAge);return t;}
    public static AcquireToolTask load(CompoundTag t){return new AcquireToolTask(t.getUUID("settlement"),t.getUUID("reservation"),t.getInt("ticks"),t.getInt("pathAge"));}
}
