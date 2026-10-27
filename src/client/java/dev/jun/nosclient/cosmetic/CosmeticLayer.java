package dev.jun.nosclient.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.jun.nosclient.gui.CosmeticScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Pose;

/** One batched submission per item; captures immutable vertices for the deferred render queue. */
public final class CosmeticLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	public CosmeticLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) { super(parent); }
	public static boolean backBlocked(AvatarRenderState s) {
		return s.isFallFlying || s.isVisuallySwimming || s.hasPose(Pose.SLEEPING)
			|| s.chestEquipment.has(DataComponents.GLIDER);
	}
	public static boolean hideVanillaCape(AvatarRenderState state) {
		CosmeticLook look = Cosmetics.look(state);
		return !state.isInvisible && !state.isSpectator && !backBlocked(state) && look.cape()
			&& Cosmetics.model(look.theme(), "cape") != null;
	}
	@Override public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yaw, float pitch) {
		if (state.isInvisible || state.isSpectator || state.distanceToCameraSq > 4096 || state.hasPose(Pose.SLEEPING)) return;
		CosmeticLook look = Cosmetics.look(state);
		if (!look.any()) return;
		double time = Cosmetics.seconds();
		boolean showroom = Minecraft.getInstance().screen instanceof CosmeticScreen;
		int packedLight = showroom ? 0xF000F0 : light;
		for (String slot : new String[]{"wings", "cape", "headband"}) {
			if (!look.visible(slot)) continue;
			boolean head = slot.equals("headband");
			if (!head && backBlocked(state)) continue;
			CosmeticMesh model = Cosmetics.model(look.theme(), slot);
			if (model == null) continue;
			CosmeticMesh.RigPose snapshot = model.pose(time, state.distanceToCameraSq > 576);
			pose.pushPose();
			(head ? getParentModel().head : getParentModel().body).translateAndRotate(pose);
			if (head && !state.headEquipment.isEmpty()) {
				pose.translate(0, -.25, 0); pose.scale(1.14f, 1.14f, 1.14f); pose.translate(0, .25, 0);
			} else if (!head && !state.chestEquipment.isEmpty()) pose.translate(0, 0, .045);
			// GLB: feet origin, Y up. Player model: shoulder origin, Y down.
			pose.translate(0, 1.5, 0); pose.scale(1, -1, 1);
			var material = showroom ? CosmeticMaterials.flat(model.texture) : RenderTypes.entityCutoutNoCull(model.texture);
			collector.submitCustomGeometry(pose, material, (captured, out) -> model.emit(snapshot, captured, out, packedLight));
			pose.popPose();
		}
	}
}
