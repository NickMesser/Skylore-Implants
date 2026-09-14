package net.messer.augment.client;

import net.messer.augment.*;
import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Quads exported from the supplied Blockbench model, including UVs and six telescoping leaves. */
public final class ChamberRenderer implements BlockEntityRenderer<ChamberEntity> {
    private record Face(float[][] vertices,float[] uv,int texture,float[] normal){}
    private record Cube(List<Face> faces,float[] slide){}
    private static final List<Cube> CUBES=load();
    private static final ResourceLocation[] TEXTURES={ResourceLocation.fromNamespaceAndPath("augment","textures/block/chamber_metal.png"),ResourceLocation.fromNamespaceAndPath("augment","textures/block/chamber_glass.png"),ResourceLocation.fromNamespaceAndPath("augment","textures/block/chamber_light.png")};
    public ChamberRenderer(BlockEntityRendererProvider.Context context){}
    private static List<Cube> load(){
        try(var in=ChamberRenderer.class.getResourceAsStream("/assets/augment/chamber_geometry.json")){
            var array=JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8)).getAsJsonArray();
            var gson=new Gson();var cubes=new ArrayList<Cube>();
            for(var e:array)cubes.add(gson.fromJson(e,Cube.class));return cubes;
        }catch(IOException e){throw new ExceptionInInitializerError(e);}
    }
    @Override public void render(ChamberEntity be,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        pose.pushPose();pose.translate(.5,0,.5);
        pose.mulPose(Axis.YP.rotationDegrees(180-be.getBlockState().getValue(ChamberBlock.FACING).toYRot()));
        float closed=Mth.lerp(partial,be.previousDoor,be.door),opened=1-closed*closed*(3-2*closed);
        for(var cube:CUBES){
            pose.pushPose();pose.translate(cube.slide[0]*opened,cube.slide[1]*opened,cube.slide[2]*opened);
            var matrix=pose.last();
            for(var face:cube.faces){
                var buffer=buffers.getBuffer(face.texture==1?RenderType.entityTranslucent(TEXTURES[1]):RenderType.entityCutoutNoCull(TEXTURES[face.texture]));
                float[] uv=face.uv;float[][] tex={{uv[0],uv[3]},{uv[2],uv[3]},{uv[2],uv[1]},{uv[0],uv[1]}};
                for(int i=0;i<4;i++){float[] v=face.vertices[i];buffer.addVertex(matrix.pose(),v[0],v[1],v[2]).setColor(255,255,255,255).setUv(tex[i][0],tex[i][1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(face.texture==2?LightTexture.FULL_BRIGHT:light).setNormal(matrix,face.normal[0],face.normal[1],face.normal[2]);}
            }
            pose.popPose();
        }
        pose.popPose();
    }
    @Override public boolean shouldRenderOffScreen(ChamberEntity be){return true;}
}
