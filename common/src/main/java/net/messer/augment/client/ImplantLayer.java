package net.messer.augment.client;

import net.messer.augment.*;
import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Draws each worn implant on the body part it is installed in. Geometry comes from
 * assets/augment/implant_models, exported by art/implants/implant_art.py as quads already
 * in ModelPart space, so each slot just follows its part's pose. Glowing pixels live in a
 * separate texture drawn again with the full-bright eyes render type.
 */
public final class ImplantLayer extends RenderLayer<AbstractClientPlayer,PlayerModel<AbstractClientPlayer>> {
    private record Quad(float[][] v,float[] uv,float[] n){}
    private record Slot(String part,List<Quad> quads){}
    private record Model(ResourceLocation texture,ResourceLocation glow,Map<String,Slot> slots){}
    private static final Map<String,Optional<Model>> MODELS=new HashMap<>();
    /** Arm implants are drawn for 4-wide arms; slim arms are 3 wide with the same inner edge. */
    private static final float SLIM=.75f;

    public ImplantLayer(RenderLayerParent<AbstractClientPlayer,PlayerModel<AbstractClientPlayer>> parent){super(parent);}

    private static Optional<Model> load(String id){
        try(var in=ImplantLayer.class.getResourceAsStream("/assets/augment/implant_models/"+id+".json")){
            if(in==null)return Optional.empty();
            var json=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
            var gson=new Gson();var slots=new HashMap<String,Slot>();
            for(var e:json.getAsJsonObject("slots").entrySet()){
                var o=e.getValue().getAsJsonObject();
                slots.put(e.getKey(),new Slot(o.get("part").getAsString(),List.of(gson.fromJson(o.get("quads"),Quad[].class))));
            }
            var tex=ResourceLocation.fromNamespaceAndPath(Augment.ID,"textures/implant/"+id+".png");
            var glow=json.get("glow").getAsBoolean()?ResourceLocation.fromNamespaceAndPath(Augment.ID,"textures/implant/"+id+"_glow.png"):null;
            return Optional.of(new Model(tex,glow,slots));
        }catch(IOException|RuntimeException e){return Optional.empty();}
    }

    private ModelPart part(String name){
        var m=getParentModel();
        return switch(name){
            case "head"->m.head; case "body"->m.body;
            case "right_arm"->m.rightArm; case "left_arm"->m.leftArm;
            case "right_leg"->m.rightLeg; default->m.leftLeg;
        };
    }

    @Override public void render(PoseStack pose,MultiBufferSource buffers,int light,AbstractClientPlayer player,float limbSwing,float limbSwingAmount,float partial,float age,float yaw,float pitch){
        if(player.isInvisible())return;
        var worn=ImplantData.worn(player.getUUID());
        if(worn.isEmpty())return;
        boolean slim=player.getSkin().model()==PlayerSkin.Model.SLIM;
        int overlay=LivingEntityRenderer.getOverlayCoords(player,0);
        for(String slotId:worn.getAllKeys()){
            var model=MODELS.computeIfAbsent(worn.getString(slotId),ImplantLayer::load).orElse(null);
            var slot=model==null?null:model.slots.get(slotId);
            if(slot==null)continue;
            var part=part(slot.part);
            if(!part.visible)continue;
            pose.pushPose();
            part.translateAndRotate(pose);
            if(slim&&slot.part.endsWith("_arm")){
                float inner=slot.part.startsWith("right")?1/16f:-1/16f;
                pose.translate(inner,0,0);pose.scale(SLIM,1,1);pose.translate(-inner,0,0);
            }
            draw(pose,buffers.getBuffer(RenderType.entityCutout(model.texture)),slot,light,overlay);
            if(model.glow!=null)draw(pose,buffers.getBuffer(RenderType.eyes(model.glow)),slot,LightTexture.FULL_BRIGHT,overlay);
            pose.popPose();
        }
    }

    private static void draw(PoseStack pose,VertexConsumer buffer,Slot slot,int light,int overlay){
        var matrix=pose.last();
        for(var q:slot.quads){
            float[] uv=q.uv;float[][] tex={{uv[0],uv[3]},{uv[2],uv[3]},{uv[2],uv[1]},{uv[0],uv[1]}};
            // Flipping y into ModelPart space mirrored the Blockbench winding; walk it backwards so front faces survive culling.
            for(int i=3;i>=0;i--){float[] v=q.v[i];
                buffer.addVertex(matrix.pose(),v[0],v[1],v[2]).setColor(255,255,255,255).setUv(tex[i][0],tex[i][1]).setOverlay(overlay).setLight(light).setNormal(matrix,q.n[0],q.n[1],q.n[2]);}
        }
    }
}
