package com.tyes.civilisation.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tyes.civilisation.CivilisationMod;
import com.tyes.civilisation.entity.CivilianEntity;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Resource-backed human renderer. Appearance selection is synced state, never gameplay state. */
public final class CivilianRenderer extends HumanoidMobRenderer<CivilianEntity, CivilianModel> {
    private static final ResourceLocation FALLBACK =
            ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");
    private static final Set<ResourceLocation> WARNED_MISSING = new HashSet<>();
    private final ResourceManager resources;

    public CivilianRenderer(EntityRendererProvider.Context context) {
        super(context, new CivilianModel(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
        resources = context.getResourceManager();
        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidArmorModel(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
        addLayer(new VisualItemLayer(this, context));
    }

    @Override
    public ResourceLocation getTextureLocation(CivilianEntity entity) {
        ResourceLocation texture = entity.appearanceDefinition().baseTexture();
        if (resources.getResource(texture).isPresent()) return texture;
        if (WARNED_MISSING.add(texture)) {
            CivilisationMod.LOGGER.warn("Missing civilian appearance texture {}; using {}", texture, FALLBACK);
        }
        return FALLBACK;
    }

    /** The synced food is a one-count visual copy and never enters equipment or game inventory. */
    private static final class VisualItemLayer extends ItemInHandLayer<CivilianEntity, CivilianModel> {
        private VisualItemLayer(CivilianRenderer renderer, EntityRendererProvider.Context context) {
            super(renderer, context.getItemInHandRenderer());
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                           CivilianEntity entity, float limbSwing, float limbSwingAmount,
                           float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
            ItemStack visual = entity.visualItem();
            if (visual.isEmpty()) return;
            poseStack.pushPose();
            renderArmWithItem(entity, visual, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                    HumanoidArm.RIGHT, poseStack, buffer, packedLight);
            poseStack.popPose();
        }
    }
}
