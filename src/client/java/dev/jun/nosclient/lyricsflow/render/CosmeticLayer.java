package dev.jun.nosclient.lyricsflow.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.math.Axis;
import dev.jun.nosclient.lyricsflow.LyricsFlow;
import dev.jun.nosclient.lyricsflow.config.ConfigManager;
import dev.jun.nosclient.lyricsflow.config.LFConfig;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.animal.axolotl.AxolotlModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.AxolotlRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.axolotl.Axolotl.Variant;
import net.minecraft.world.entity.player.Player;

public final class CosmeticLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
   private static final Identifier WHITE = LyricsFlow.id("textures/misc/white.png");
   private static final float PX = 0.0625F;
   static final float HELMET_FIT = 1.17F;
   private static Map<String, List<CosmeticLayer.Group>> all;
   private static final Identifier AXOLOTL_BLUE = Identifier.withDefaultNamespace("textures/entity/axolotl/axolotl_blue.png");
   private static AxolotlModel axolotlModel;
   private static final AxolotlRenderState AXO_STATE = new AxolotlRenderState();
   private static final float AXOLOTL_SCALE = 0.5F;

   public CosmeticLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
      super(parent);
   }

   public void submit(PoseStack pose, SubmitNodeCollector queue, int light, AvatarRenderState state, float yRot, float xRot) {
      LFConfig c = ConfigManager.get();
      Minecraft mc = Minecraft.getInstance();
      if (c.enabled && mc.player != null && !state.isInvisible && !state.isSpectator) {
         String cos = Cosmetics.forEntity(state.id);
         Player owner = Cosmetics.playerOf(state.id);
         if (owner != null && !"none".equals(cos)) {
            if ("axolotl".equals(cos)) {
               this.axolotl(pose, queue, light, owner);
            } else if ("kuz3y_wqq".equals(cos)) {
               this.buddy(pose, queue, light, owner, state.id, yRot, xRot);
            } else {
               if (all == null) {
                  load();
               }

               List<CosmeticLayer.Group> groups = all.get(cos);
               if (groups != null) {
                  double t = System.nanoTime() / 1.0E9 % 3600.0;
                  PlayerModel model = (PlayerModel)this.getParentModel();
                  boolean helmetOn = !owner.getItemBySlot(EquipmentSlot.HEAD).isEmpty();

                  for (CosmeticLayer.Group g : groups) {
                     ModelPart part = "body".equals(g.part()) ? model.body : model.head;
                     if (part.visible) {
                        pose.pushPose();
                        part.translateAndRotate(pose);
                        float lift = g.lift();
                        if ("head".equals(g.part()) && helmetOn) {
                           lift *= 1.17F;
                        }

                        if (lift != 1.0F) {
                           pose.translate(0.0F, -0.25F, 0.0F);
                           pose.scale(lift, lift, lift);
                           pose.translate(0.0F, 0.25F, 0.0F);
                        }

                        String a = g.anim();
                        if (a.equals("bob")) {
                           pose.translate(0.0F, -((float)Math.sin(t * 2.2)) * 0.6F * 0.0625F, 0.0F);
                        } else if (a.startsWith("sway:")) {
                           String[] q = a.split(":");
                           float deg = Float.parseFloat(q[1]) * (float)Math.sin(t * 2.4);
                           float py = Float.parseFloat(q[3]) * 0.0625F;
                           float pz = Float.parseFloat(q[4]) * 0.0625F;
                           pose.translate(0.0F, py, pz);

                           pose.mulPose(switch (q[2]) {
                              case "x" -> Axis.XP.rotationDegrees(deg);
                              case "y" -> Axis.YP.rotationDegrees(deg);
                              default -> Axis.ZP.rotationDegrees(deg);
                           });
                           pose.translate(0.0F, -py, -pz);
                        } else if (a.startsWith("flap:")) {
                           int side = Integer.parseInt(a.substring(5));
                           float beat = (float)(0.5 + 0.5 * Math.sin(t * 2.6));
                           float deg = -side * (6.0F + 30.0F * beat);
                           pose.translate(0.0F, 0.0F, 0.21875F);
                           pose.mulPose(Axis.YP.rotationDegrees(deg));
                           pose.mulPose(Axis.ZP.rotationDegrees(side * 6.0F * (beat - 0.5F)));
                           pose.translate(0.0F, 0.0F, -0.21875F);
                        }

                        RenderType type = g.alpha() < 255 && !g.glow() ? RenderTypes.entityTranslucent(WHITE) : RenderTypes.entityCutoutNoCull(WHITE);
                        int lt = g.glow() ? 15728880 : light;
                        queue.submitCustomGeometry(pose, type, (p, vc) -> emit(vc, p, g, lt));
                        pose.popPose();
                     }
                  }
               }
            }
         }
      }
   }

   private void axolotl(PoseStack pose, SubmitNodeCollector queue, int light, Player owner) {
      ModelPart head = ((PlayerModel)this.getParentModel()).head;
      if (head.visible) {
         try {
            if (axolotlModel == null) {
               axolotlModel = new AxolotlModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.AXOLOTL));
            }

            CosmeticLayer.AxolotlRenderStateSetup.apply(AXO_STATE, (float)(System.nanoTime() / 1.0E9 % 3600.0 * 20.0));
            axolotlModel.setupAnim(AXO_STATE);
            pose.pushPose();
            head.translateAndRotate(pose);
            boolean helm = !owner.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
            pose.translate(0.0F, (helm ? -9.2F : -8.0F) * 0.0625F, 0.0F);
            pose.scale(0.5F, 0.5F, 0.5F);
            pose.translate(0.0F, -1.375F, 0.0F);
            queue.submitModelPart(axolotlModel.root(), pose, RenderTypes.entityCutoutNoCull(AXOLOTL_BLUE), light, OverlayTexture.NO_OVERLAY, null);
            pose.popPose();
         } catch (Throwable e) {
            LyricsFlow.LOG.warn("[LyricsFlow] Axolotl cosmetic failed: {}", e.toString());
         }
      }
   }

   private void buddy(PoseStack pose, SubmitNodeCollector queue, int light, Player owner, int id, float headYaw, float headPitch) {
      ModelPart body = ((PlayerModel)this.getParentModel()).body;
      if (body.visible) {
         PlayerModel b = Cosmetics.buddyFor(id, System.nanoTime() / 1.0E9 % 3600.0, headYaw, headPitch);
         if (b != null) {
            boolean chest = !owner.getItemBySlot(EquipmentSlot.CHEST).isEmpty();
            pose.pushPose();
            body.translateAndRotate(pose);
            pose.translate((chest ? -6.9F : -6.2F) * 0.0625F, (chest ? -1.2F : -0.2F) * 0.0625F, 0.0F);
            float k = 0.32F;
            pose.scale(k, k, k);
            pose.translate(0.0F, -0.75F, 0.0F);
            pose.mulPose(Axis.YP.rotation((float)Math.toRadians(Mth.clamp(headYaw, -80.0F, 80.0F))));
            queue.submitModelPart(b.root(), pose, RenderTypes.entityTranslucent(Cosmetics.KUZ3Y), light, OverlayTexture.NO_OVERLAY, null);
            pose.popPose();
         }
      }
   }

   private static void emit(VertexConsumer vc, Pose p, CosmeticLayer.Group g, int light) {
      float[] q = g.quads();
      int alpha = g.alpha() << 24;
      boolean both = false;

      for (int i = 0; i < q.length; i += 16) {
         int col = alpha | Float.floatToRawIntBits(q[i]) & 16777215;
         float nx = g.glow() ? 0.0F : q[i + 1];
         float ny = g.glow() ? 1.0F : q[i + 2];
         float nz = g.glow() ? 0.0F : q[i + 3];

         for (int k = 0; k < 4; k++) {
            v(vc, p, q[i + 4 + k * 3], q[i + 5 + k * 3], q[i + 6 + k * 3], col, light, nx, ny, nz);
         }

         if (both) {
            for (int k = 3; k >= 0; k--) {
               v(vc, p, q[i + 4 + k * 3], q[i + 5 + k * 3], q[i + 6 + k * 3], col, light, -nx, -ny, -nz);
            }
         }
      }
   }

   private static void v(VertexConsumer vc, Pose p, float x, float y, float z, int col, int light, float nx, float ny, float nz) {
      vc.addVertex(p, x, y, z)
         .setColor(col)
         .setUv(0.5F, 0.5F)
         .setOverlay(OverlayTexture.NO_OVERLAY)
         .setLight(light)
         .setNormal(p, nx, ny, nz);
   }

   private static synchronized void load() {
      if (all == null) {
         Map<String, List<CosmeticLayer.Group>> out = new HashMap<>();

         try (InputStream in = CosmeticLayer.class.getResourceAsStream("/assets/lyricsflow/cosmetics.json")) {
            if (in != null) {
               for (JsonElement e : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray()) {
                  JsonObject o = e.getAsJsonObject();
                  List<CosmeticLayer.Group> gs = new ArrayList<>();

                  for (JsonElement ge : o.getAsJsonArray("groups")) {
                     gs.add(bake(ge.getAsJsonObject()));
                  }

                  out.put(o.get("id").getAsString(), gs);
               }
            }
         } catch (Exception ex) {
            LyricsFlow.LOG.warn("[LyricsFlow] Could not load cosmetics: {}", ex.toString());
         }

         all = out;
      }
   }

   private static long key(int x, int y, int z) {
      return (long)(x + 4096) << 26 | (long)(y + 4096) << 13 | z + 4096;
   }

   private static CosmeticLayer.Group bake(JsonObject o) {
      float s = o.get("size").getAsFloat() * 0.0625F;
      float oy = o.has("oy") ? o.get("oy").getAsFloat() * 0.0625F : 0.0F;
      JsonArray v = o.getAsJsonArray("v");
      int n = v.size() / 4;
      int[] xs = new int[n];
      int[] ys = new int[n];
      int[] zs = new int[n];
      int[] cs = new int[n];
      Set<Long> filled = new HashSet<>();

      for (int i = 0; i < n; i++) {
         xs[i] = v.get(i * 4).getAsInt();
         ys[i] = v.get(i * 4 + 1).getAsInt();
         zs[i] = v.get(i * 4 + 2).getAsInt();
         cs[i] = v.get(i * 4 + 3).getAsInt();
         filled.add(key(xs[i], ys[i], zs[i]));
      }

      boolean glow = o.get("glow").getAsBoolean();
      List<Float> q = new ArrayList<>();

      for (int i = 0; i < n; i++) {
         int x = xs[i];
         int y = ys[i];
         int z = zs[i];
         int rgb = cs[i];
         float x0 = x * s;
         float x1 = (x + 1) * s;
         float y0 = y * s + oy;
         float y1 = (y + 1) * s + oy;
         float z0 = z * s;
         float z1 = (z + 1) * s;
         if (!filled.contains(key(x, y - 1, z))) {
            face(q, rgb, glow ? 1.0F : 1.0F, 0.0F, -1.0F, 0.0F, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
         }

         if (!filled.contains(key(x, y + 1, z))) {
            face(q, rgb, glow ? 1.0F : 0.55F, 0.0F, 1.0F, 0.0F, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
         }

         if (!filled.contains(key(x, y, z - 1))) {
            face(q, rgb, glow ? 1.0F : 0.9F, 0.0F, 0.0F, -1.0F, x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0);
         }

         if (!filled.contains(key(x, y, z + 1))) {
            face(q, rgb, glow ? 1.0F : 0.8F, 0.0F, 0.0F, 1.0F, x1, y1, z1, x0, y1, z1, x0, y0, z1, x1, y0, z1);
         }

         if (!filled.contains(key(x - 1, y, z))) {
            face(q, rgb, glow ? 1.0F : 0.72F, -1.0F, 0.0F, 0.0F, x0, y1, z1, x0, y1, z0, x0, y0, z0, x0, y0, z1);
         }

         if (!filled.contains(key(x + 1, y, z))) {
            face(q, rgb, glow ? 1.0F : 0.72F, 1.0F, 0.0F, 0.0F, x1, y1, z0, x1, y1, z1, x1, y0, z1, x1, y0, z0);
         }
      }

      float[] arr = new float[q.size()];

      for (int i = 0; i < arr.length; i++) {
         arr[i] = q.get(i);
      }

      String part = o.get("part").getAsString();
      return new CosmeticLayer.Group(
         part, o.get("anim").getAsString(), glow, o.get("alpha").getAsInt(), arr, !"head".equals(part) ? 1.0F : (o.has("oy") ? 1.01F : 1.08F)
      );
   }

   private static void face(List<Float> q, int rgb, float k, float nx, float ny, float nz, float... c) {
      int r = Math.min(255, (int)((rgb >> 16 & 0xFF) * k));
      int g = Math.min(255, (int)((rgb >> 8 & 0xFF) * k));
      int b = Math.min(255, (int)((rgb & 0xFF) * k));
      q.add(Float.intBitsToFloat(r << 16 | g << 8 | b));
      q.add(nx);
      q.add(ny);
      q.add(nz);

      for (float f : c) {
         q.add(f);
      }
   }

   private static final class AxolotlRenderStateSetup {
      static void apply(AxolotlRenderState s, float age) {
         s.variant = Variant.BLUE;
         s.onGroundFactor = 1.0F;
         s.inWaterFactor = 0.0F;
         s.movingFactor = 0.0F;
         s.playingDeadFactor = 0.0F;
         s.ageInTicks = age;
         s.xRot = 0.0F;
         s.yRot = 0.0F;
         s.walkAnimationPos = 0.0F;
         s.walkAnimationSpeed = 0.0F;
      }
   }

   private record Group(String part, String anim, boolean glow, int alpha, float[] quads, float lift) {
   }
}
