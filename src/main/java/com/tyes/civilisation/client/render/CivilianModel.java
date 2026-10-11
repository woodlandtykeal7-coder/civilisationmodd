package com.tyes.civilisation.client.render;

import com.tyes.civilisation.entity.CitizenVisualState;
import com.tyes.civilisation.entity.CivilianEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** Player-geometry model that layers semantic, presentation-only task movement over locomotion. */
public final class CivilianModel extends PlayerModel<CivilianEntity> {
    public CivilianModel(ModelPart root) {
        super(root, false);
    }

    @Override
    public void setupAnim(CivilianEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        CitizenVisualState state = entity.visualState();
        if (state == CitizenVisualState.WORKING || state == CitizenVisualState.HARVESTING
                || state == CitizenVisualState.CHOPPING) {
            float workCycle = Mth.sin(ageInTicks * 0.45F);
            float reach = state == CitizenVisualState.CHOPPING ? -1.35F : -1.05F;
            rightArm.xRot = reach + workCycle * 0.55F;
            leftArm.xRot = -0.85F - workCycle * 0.35F;
            rightArm.yRot = -0.12F;
            leftArm.yRot = 0.12F;
        } else if (state == CitizenVisualState.EATING) {
            if (entity.visualItem().isEmpty()) {
                rightArm.xRot = -0.95F + Mth.sin(ageInTicks * 0.35F) * 0.12F;
                rightArm.yRot = -0.15F;
            } else {
                rightArm.xRot = -1.42F + Mth.sin(ageInTicks * 0.55F) * 0.08F;
                rightArm.yRot = -0.28F;
                head.xRot += 0.08F;
            }
        }
        // PlayerModel copied these before the semantic animation above, so refresh overlay parts.
        leftSleeve.copyFrom(leftArm);
        rightSleeve.copyFrom(rightArm);
        leftPants.copyFrom(leftLeg);
        rightPants.copyFrom(rightLeg);
        jacket.copyFrom(body);
    }
}
