// SPDX-License-Identifier: AGPL-3.0-only
// Original Folia diorama camera/geometry adapted to Canvas. Source and differences: third_party/folia/NOTICE.md.
package com.zuoqirun.lyricscompanion;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.LruCache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.zuoqirun.lyricscompanion.FoliaDioramaMath.*;

/** Native perspective projection of Folia's staged lyric corridor, preserving continuous camera velocity. */
final class FoliaOriginalDioramaRenderer {
    private final Paint text=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG|Paint.LINEAR_TEXT_FLAG);
    private final Paint dots=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix plane=new Matrix();
    private final float[] source={0,0,1,0,1,1,0,1},destination=new float[8];
    private final Vec camera=new Vec(),look=new Vec(),renderCamera=new Vec();
    private final double[] velocity=new double[3],lookVelocity=new double[3];
    private final Quaternion orient=new Quaternion(),aligned=new Quaternion();
    private final LruCache<String,FoliaDioramaSurface> surfaces=new LruCache<>(32);
    private final LruCache<Integer,List<Shape>> formations=new LruCache<>(20);
    private final LruCache<Integer,LineLayout> layouts=new LruCache<>(20);
    private final Ripple[] ripples=new Ripple[9];
    private final Band[] bands={new Band(),new Band(),new Band()};
    private final int[] rippleCursor=new int[3];
    private Frame[] frames;
    private List<LrcTimeline.Line> lines=Collections.emptyList(),oldLines=Collections.emptyList();
    private Frame[] oldFrames;
    private String seed="",oldSeed="",geometrySeed="",trackIdentity="";
    private Typeface face=Typeface.create("sans-serif",Typeface.NORMAL);
    private float fontScale=1,strength=.5f,tracking;
    private boolean animate=true,lowResource,initialized,showTranslation=true;
    private long lastPosition=-1,flightStart=-1;
    private int lastAnchor=-1,oldAnchor=-1,epoch;
    private double width,height,focal,time,animationTime,dt,alignWeight,pulse=1,pulseVelocity;
    private double amplitude=.34,detail,offsetGain,centroid,power;
    private double flightLength;
    private int flightSide=1;
    private final Vec flightPerp=new Vec(1,0,0),right=new Vec(1,0,0),up=new Vec(0,1,0),forward=new Vec(0,0,-1);
    private int primary,accent,muted;

    FoliaOriginalDioramaRenderer(){for(int i=0;i<ripples.length;i++)ripples[i]=new Ripple();}

    void setGeometrySeed(String value) {
        String normalized = value == null ? "" : value;
        if (geometrySeed.equals(normalized)) return;
        geometrySeed = normalized;
        clear();
    }

    void setTracking(float tracking) {
        if (this.tracking != tracking) {
            this.tracking = tracking;
            layouts.evictAll();
        }
    }

    void configure(Typeface face,float fontScale,float strength,boolean showTranslation,boolean animate,boolean lowResourceMode){
        Typeface resolved=face==null?Typeface.create("sans-serif",Typeface.NORMAL):face;
        if(!resolved.equals(this.face)||fontScale!=this.fontScale)layouts.evictAll();
        this.face=resolved;this.fontScale=fontScale;this.strength=strength;
        this.showTranslation=showTranslation;this.animate=animate;
        if(lowResource!=lowResourceMode){surfaces.evictAll();lowResource=lowResourceMode;}
    }
    boolean transitioning(){return flightStart>=0||Math.abs(velocity[0])+Math.abs(velocity[1])+Math.abs(velocity[2])>.001;}
    void clear(){
        lines=Collections.emptyList();oldLines=Collections.emptyList();frames=null;oldFrames=null;seed="";trackIdentity="";
        surfaces.evictAll();formations.evictAll();layouts.evictAll();initialized=false;lastPosition=-1;lastAnchor=-1;flightStart=-1;
        for(int i=0;i<3;i++){velocity[i]=lookVelocity[i]=0;bands[i].clear();}for(Ripple ripple:ripples)ripple.strength=0;
        pulse=1;pulseVelocity=0;alignWeight=0;
    }

    void draw(Canvas canvas,MusicSnapshot snapshot,List<LrcTimeline.Line> incoming,int anchor,long lyricTime,
              long animationClock,long delta,float width,float height,float bass,int primary,int accent,int muted){
        this.width=width;this.height=height;this.focal=height/(2*Math.tan(Math.toRadians(FOV)/2));
        this.time=lyricTime/1000.;this.animationTime=animate?animationClock/1000.:0;
        this.dt=animate&&snapshot.playing?Math.max(0,Math.min(.1,delta/1000.)):0;
        this.primary=primary;this.accent=accent;this.muted=muted;
        String identity=snapshot.sourceName+'\n'+snapshot.title+'\n'+snapshot.artist;
        boolean loop=lastPosition>lyricTime+500&&anchor<=1&&lastAnchor>1;
        if(frames==null||!identity.equals(trackIdentity)||loop||incoming!=lines){
            boolean changed=frames!=null&&(!identity.equals(trackIdentity)||loop);
            if(changed){oldFrames=frames;oldLines=lines;oldSeed=seed;oldAnchor=lastAnchor;}
            lines=incoming;trackIdentity=identity;seed=geometrySeed.isEmpty()?identity:geometrySeed;
            frames=path(Math.max(1,lines.size()),seed);formations.evictAll();layouts.evictAll();
            if(changed&&initialized){
                epoch++;Vec offset=transitionOffset(seed,epoch).add(frameAt(oldFrames,Math.max(0,oldAnchor)).position);
                for(Frame frame:frames)frame.position.add(offset);
                flightStart=animationClock;flightLength=0;
                flightSide=unit(hashSeed(seed)+epoch*17)<.5?-1:1;
            }
        }
        if(lines.isEmpty())return;
        int current=Math.max(0,Math.min(lines.size()-1,anchor));
        boolean seek=lastPosition>=0&&!loop&&(lyricTime<lastPosition-500||lyricTime>lastPosition+2500);
        LineLayout active=layout(current);Frame frame=frames[current];Placement p=placement(current,seed,1);
        LrcTimeline.Line line=lines.get(current);double end=lineEnd(current),progress=clamp((time-line.timeMs/1000.)/Math.max(.001,end-line.timeMs/1000.),0,1);
        double wordProgress=active.progress(time),fit=frameFit(active.advance*.62/active.font,width/height)*p.scale*fontScale;
        double trackWidth=active.advance*.62/active.font*fit,settle=holdSettle(time-end);
        double truck=truck(wordProgress,trackWidth,HERO*Math.tan(Math.toRadians(FOV)/2)*width/height*.92)*settle;
        Vec head=frame.local(p.r+truck,p.u,0),shot=shotOffset(shot(current,lines,seed),progress,wordProgress,current,seed,settle),drift=drift(time,seed,.62);
        Vec target=new Vec(head.x+frame.right.x*(shot.x+drift.x)+frame.up.x*(shot.y+drift.y)-frame.forward.x*(shot.z+drift.z),
                head.y+frame.right.y*(shot.x+drift.x)+frame.up.y*(shot.y+drift.y)-frame.forward.y*(shot.z+drift.z),
                head.z+frame.right.z*(shot.x+drift.x)+frame.up.z*(shot.y+drift.y)-frame.forward.z*(shot.z+drift.z));
        Vec aim=new Vec(head.x+frame.right.x*p.look*settle,head.y+frame.right.y*p.look*settle,head.z+frame.right.z*p.look*settle);
        boolean flying=flightStart>=0;double flightT=flying?clamp((animationClock-flightStart)/3200.,0,1):1;
        if(flying&&flightLength==0){flightLength=camera.distance(target);flightPerp.set(target.z-camera.z,0,-(target.x-camera.x)).normalize();}
        if(flightT>=1&&flying){flightStart=-1;oldFrames=null;oldLines=Collections.emptyList();flying=false;}
        double ease=flightT*flightT*flightT*(flightT*(flightT*6-15)+10),smoothTime=flying?1.1+(.42-1.1)*ease:.42;
        double envelope=flying?Math.pow(Math.sin(Math.PI*flightT),2):0,arc=envelope*flightLength*.16,sweep=envelope*flightLength*.14*flightSide;
        aim.x+=flightPerp.x*sweep;aim.y+=flightPerp.y*sweep;aim.z+=flightPerp.z*sweep;
        boolean snap=!initialized||seek||(!flying&&camera.distance(target)>24);
        if(snap){camera.set(target);look.set(aim);for(int i=0;i<3;i++)velocity[i]=lookVelocity[i]=0;}
        else if(dt>0){
            camera.set(damp(camera.x,target.x,velocity,0,smoothTime,dt),damp(camera.y,target.y,velocity,1,smoothTime,dt),damp(camera.z,target.z,velocity,2,smoothTime,dt));
            look.set(damp(look.x,aim.x,lookVelocity,0,smoothTime,dt),damp(look.y,aim.y,lookVelocity,1,smoothTime,dt),damp(look.z,aim.z,lookVelocity,2,smoothTime,dt));
        }
        renderCamera.set(camera.x+flightPerp.x*arc,camera.y+flightPerp.y*arc+arc*.5,camera.z+flightPerp.z*arc);
        orient(frame,p,current,line,end,snap,envelope*.14*flightSide);
        initialized=true;lastPosition=lyricTime;lastAnchor=current;
        audio(bass);
        drawDust(canvas,current);
        // Far-to-near composition mirrors perspective depth; each lattice is deterministic per song/line.
        for(int i=Math.min(lines.size()-1,current+3);i>=Math.max(0,current-2);i--)drawFormation(canvas,i);
        if(oldFrames!=null)for(int i=Math.min(oldLines.size()-1,oldAnchor+1);i>=Math.max(0,oldAnchor-2);i--)drawOldLine(canvas,i);
        for(int i=Math.min(lines.size()-1,current+3);i>=Math.max(0,current-2);i--)drawLine(canvas,i,current);
    }

    private void orient(Frame frame,Placement p,int current,LrcTimeline.Line line,double end,boolean snap,double bank){
        Vec f=Vec.sub(look,renderCamera).normalize(),r=Vec.cross(f,new Vec(0,1,0)).normalize(),u=Vec.cross(r,f).normalize();
        Quaternion looking=Quaternion.basis(r,u,new Vec(-f.x,-f.y,-f.z));
        Quaternion targetAlign=Quaternion.basis(frame.right,frame.up,new Vec(-frame.forward.x,-frame.forward.y,-frame.forward.z));
        targetAlign.multiply(Quaternion.axis(0,1,0,p.yaw)).multiply(Quaternion.axis(0,0,1,p.roll));
        if(snap)aligned.set(targetAlign);else aligned.slerp(targetAlign,1-Math.exp(-2.6*dt));
        double[] ceilings={.85,.7,.55,.85,.7,.9,.8,.5,.6,.5,.62,.78,.72};
        double need=Math.min(smooth((time-(line.timeMs/1000.-.6))/.95),1-smooth((time-end)/1))*ceilings[shot(current,lines,seed)];
        if(snap)alignWeight=need;else alignWeight+=(need-alignWeight)*(1-Math.exp(-1.2*dt));
        Quaternion target=new Quaternion().set(looking).slerp(aligned,alignWeight);
        Vec view=target.rotate(new Vec(0,0,-1));
        double off=Math.acos(clamp(view.dot(f),-1,1)),halfV=Math.toRadians(FOV)/2,halfH=Math.atan(Math.tan(halfV)*width/height),limit=Math.min(halfV,halfH)*.7;
        if(off>limit)target.slerp(looking,1-limit/off);
        if(snap)orient.set(target);else orient.slerp(target,1-Math.exp(-4.2*dt));
        Quaternion shown=new Quaternion().set(orient).multiply(Quaternion.axis(0,0,1,bank));
        right.set(shown.rotate(new Vec(1,0,0)));up.set(shown.rotate(new Vec(0,1,0)));forward.set(shown.rotate(new Vec(0,0,-1)));
    }

    private boolean project(Vec world,float[] out,int offset){
        double x=world.x-renderCamera.x,y=world.y-renderCamera.y,z=world.z-renderCamera.z,depth=x*forward.x+y*forward.y+z*forward.z;
        if(depth<=.1||depth>45)return false;
        out[offset]=(float)(width*.5+(x*right.x+y*right.y+z*right.z)*focal/depth);
        out[offset+1]=(float)(height*.5-(x*up.x+y*up.y+z*up.z)*focal/depth);return true;
    }
    private double lineEnd(int index){LrcTimeline.Line line=lines.get(index);long end=line.timeMs+line.durationMs;for(LrcTimeline.Word word:line.words)end=Math.max(end,word.startMs+word.durationMs);if(end<=line.timeMs)end=index+1<lines.size()?lines.get(index+1).timeMs:line.timeMs+5000;return end/1000.;}
    private LineLayout layout(int index){LineLayout found=layouts.get(index);if(found!=null)return found;text.setTypeface(face);text.setTextSize(96);found=new LineLayout(lines.get(index),text,lineEnd(index),tracking);layouts.put(index,found);return found;}
    private void drawLine(Canvas canvas,int index,int current){
        Placement p=placement(index,seed,1);Frame frame=frames[index];LineLayout line=layout(index);Vec center=frame.local(p.r,p.u,0);
        double life=textLife(center.distance(renderCamera));int offset=index-current;
        double opacity=offset==0?1:offset==-1?.3:offset==-2?.1:offset==1?.34:offset==2?.16:.06;
        renderLine(canvas,line,frame,p,opacity*life,index==current,offset<0?primary:muted);
    }
    private void drawOldLine(Canvas canvas,int index){
        LrcTimeline.Line line=oldLines.get(index);text.setTypeface(face);text.setTextSize(96);
        LineLayout layout=new LineLayout(line,text,(line.timeMs+Math.max(1000,line.durationMs))/1000.,tracking);
        Placement p=placement(index,oldSeed,1);Frame frame=oldFrames[index];
        renderLine(canvas,layout,frame,p,(index==oldAnchor?.7:.45)*textLife(frame.local(p.r,p.u,0).distance(renderCamera)),false,primary);
    }
    private void renderLine(Canvas canvas,LineLayout line,Frame frame,Placement p,double opacity,boolean active,int color){
        if(opacity<.003)return;double scale=.62/line.font*frameFit(line.advance*.62/line.font,width/height)*p.scale*fontScale;
        Quaternion q=Quaternion.basis(frame.right,frame.up,new Vec(-frame.forward.x,-frame.forward.y,-frame.forward.z));q.multiply(Quaternion.axis(0,1,0,p.yaw)).multiply(Quaternion.axis(0,0,1,p.roll));
        Vec r=q.rotate(new Vec(1,0,0)),u=q.rotate(new Vec(0,1,0)),center=frame.local(p.r,p.u,0);
        double left=-line.advance/2,top=-line.font*1.25,bottom=line.font*.5;
        source[0]=(float)left;source[1]=(float)top;source[2]=(float)(-left);source[3]=(float)top;source[4]=(float)(-left);source[5]=(float)bottom;source[6]=(float)left;source[7]=(float)bottom;
        for(int corner=0;corner<4;corner++){
            double x=source[corner*2]*scale,y=-source[corner*2+1]*scale;
            if(!project(new Vec(center.x+r.x*x+u.x*y,center.y+r.y*x+u.y*y,center.z+r.z*x+u.z*y),destination,corner*2))return;
        }
        if(!plane.setPolyToPoly(source,0,destination,0,4))return;
        int save=canvas.save();canvas.concat(plane);text.setTypeface(face);text.setTextSize(line.font);text.setTextAlign(Paint.Align.LEFT);
        for(int i=0;i<line.glyphs.length;i++){
            double start=line.starts[i],end=line.ends[i],sung=clamp((time-start)/Math.max(.001,end-start),0,1);
            if(active&&dt>0){line.glows[i]+=(time>=start&&time<end?1-line.glows[i]:-line.glows[i])*(1-Math.exp(-(time>=start&&time<end?14:4.5)*dt));line.souls[i]+=(time>=start&&time<end?1-line.souls[i]:-line.souls[i])*(1-Math.exp(-(time>=start&&time<end?12:1.7)*dt));}
            double glow=active?line.glows[i]:0;int fill=blend(primary,accent,Math.min(1,glow*1.15));
            text.setColor(active?fill:color);text.setAlpha((int)(255*clamp(opacity*(active?.5+.42*sung:1),0,1)));
            text.clearShadowLayer();canvas.drawText(line.glyphs[i],(float)(left+line.offsets[i]),0,text);
            if(glow>.012){text.setAlpha((int)(255*clamp(opacity*glow*.9*(.6+.4*power),0,1)));text.setShadowLayer(10,0,0,fill);canvas.drawText(line.glyphs[i],(float)(left+line.offsets[i]),0,text);text.clearShadowLayer();}
            if(active&&time>=end&&line.souls[i]>.01){double flight=smooth((time-end)/.5);int ghost=canvas.save();float x=(float)(left+line.offsets[i]);canvas.translate(x,(float)(-.5*line.font*flight));canvas.scale((float)(1+.3*flight),(float)(1+.3*flight));text.setAlpha((int)(255*clamp(opacity*line.souls[i]*.6*(1-.5*flight),0,1)));canvas.drawText(line.glyphs[i],0,0,text);canvas.restoreToCount(ghost);}
        }
        text.clearShadowLayer();canvas.restoreToCount(save);
    }

    private void drawFormation(Canvas canvas,int index){
        List<Shape> list=formations.get(index);if(list==null){list=formation(index,seed,shot(index,lines,seed),frames[index],placement(index,seed,1));formations.put(index,list);}
        int ordinal=0;
        for(Shape shape:list){
            if(shape.position.distance(renderCamera)>29){ordinal++;continue;}
            String key=shape.kind+":"+(lowResource?192:576)+":"+Math.round(shape.stretch*4);FoliaDioramaSurface surface=surfaces.get(key);
            if(surface==null){surface=FoliaDioramaSurface.build(shape.kind,lowResource?192:576,shape.stretch);surfaces.put(key,surface);}
            double phase=unit(hashSeed(seed+"|"+index+"|"+ordinal+"|"+new String[]{"box","sphere","cone","torus"}[shape.kind])+31)*Math.PI*2;
            drawCloud(canvas,shape,surface,phase);ordinal++;
        }
    }

    /** Scalar CPU execution of the original continuous vertex displacement; Canvas owns rasterization. */
    private void drawCloud(Canvas canvas,Shape shape,FoliaDioramaSurface surface,double phase){
        float[] projected=new float[2];double kmax=Math.PI*2/Math.max(.0001,surface.spacing*4),spin=Math.cos(phase),spun=Math.sin(phase);
        for(int i=0;i<surface.positions.length;i+=3){
            double px=surface.positions[i],py=surface.positions[i+1],pz=surface.positions[i+2],ux=px/surface.radius,uy=py/surface.radius,uz=pz/surface.radius,audio=0;
            for(Ripple ripple:ripples)if(ripple.strength>0){double age=animationTime-ripple.birth;if(age<0||age>6)continue;double rx=ripple.x*spin-ripple.z*spun,rz=ripple.x*spun+ripple.z*spin,dx=ux-rx,dy=uy-ripple.y,dz=uz-rz,r=Math.sqrt(dx*dx+dy*dy+dz*dz),dr=r-age*ripple.speed;audio+=ripple.strength*Math.exp(-dr*dr/(ripple.width*ripple.width))*Math.sin(dr*Math.min(ripple.k,kmax))*Math.exp(-age*1.15-r*.35);}
            double fine=detail*smooth((Math.abs(audio)-.06)/.39),k=kmax*.7;audio+=Math.sin(ux*k+animationTime*3.1)*Math.sin(uy*k-animationTime*2.6)*Math.sin(uz*k+animationTime*2.2)*fine*.3;
            double idle=.11*Math.sin(ux*1.7+animationTime*.5)*Math.cos(uz*1.4-animationTime*.37)+.06*Math.sin(uy*2.1-animationTime*.66),swell=clamp(audio+idle,-1.6,1.6)*amplitude;
            double displacement=swell*surface.radius,d=clamp(Math.abs(clamp(audio,-1.6,1.6)*amplitude)/.29,0,1);
            double x=(px+surface.normals[i]*displacement)*shape.scale*pulse,y=(py*shape.stretch+surface.normals[i+1]*displacement)*shape.scale*pulse,z=(pz+surface.normals[i+2]*displacement+Math.sin(animationTime*.6+phase)*.12*offsetGain*surface.radius)*shape.scale*pulse;
            Vec world=new Vec(shape.position.x+x,shape.position.y+y,shape.position.z+z);if(!project(world,projected,0))continue;
            double life=life(world.distance(renderCamera));if(life<.003)continue;
            double dx=world.x-renderCamera.x,dy=world.y-renderCamera.y,dz=world.z-renderCamera.z,depth=dx*forward.x+dy*forward.y+dz*forward.z;
            double size=clamp((.052+(d*d*d+fine*.35)*.1)*focal/depth,1.4,26);
            double g=(px*.2+py*shape.stretch*.16+pz*.13+phase*.05+shape.color*.5+animationTime*.02+centroid*.3)*6.283;
            int color=palette(g,d,fine);double opacity=life*Math.min(1,(shape.far?.5:.72)+d*.28);
            dots.setColor(color);dots.setAlpha((int)(255*clamp(opacity,0,1)));canvas.drawCircle(projected[0],projected[1],(float)(size*.5),dots);
            if(d>.1){dots.setAlpha((int)(255*Math.min(.5,life*smooth((d-.1)/.7)*1.15*.65*.4)));canvas.drawCircle(projected[0],projected[1],(float)(size*(1.9+.65*1.3)*.5),dots);}
        }
    }

    private void drawDust(Canvas canvas,int current){
        float[] projected=new float[2];int circumference=lowResource?14:28,radial=2,total=circumference*radial;double golden=Math.PI*(3-Math.sqrt(5)),phase=unit(hashSeed(seed)+991)*Math.PI*2;
        for(int line=current-2;line<=current+5;line++){
            Frame frame=frameAt(frames,line);
            for(int ring=0;ring<radial;ring++)for(int seg=0;seg<circumference;seg++){
                int sample=ring*circumference+seg;double salt=hashSeed(seed)+line*131+sample*17,angle=phase+(line+ring)*golden+seg/(double)circumference*Math.PI*2+(unit(salt+3)-.5)*Math.PI*2/circumference;
                double radius=2.4+Math.sqrt((ring+.35+unit(salt+4)*.3)/radial)*5.2,depth=(radical(sample+1,2)+(unit(salt+2)-.5)/total-.5)*8;
                Vec world=frame.local(Math.cos(angle)*radius,Math.sin(angle)*radius*.64,depth);if(!project(world,projected,0))continue;double distance=world.distance(renderCamera),fog=1-smooth((distance-12)/18);dots.setColor(muted);dots.setAlpha((int)(100*fog));canvas.drawCircle(projected[0],projected[1],.7f,dots);
            }
        }
    }
    private static double radical(int value,int base){double result=0,fraction=1./base;while(value>0){result+=(value%base)*fraction;value/=base;fraction/=base;}return result;}
    private void audio(float bass){
        double[] levels={clamp(bass,0,1),0,0};
        if(bass>0){AudioSpectrumSource.Frame frame=AudioSpectrumSource.latestFrame();if(frame.live&&frame.levels.length>0){for(int b=1;b<3;b++){int from=frame.levels.length*b/3,to=frame.levels.length*(b+1)/3;for(int i=from;i<to;i++)levels[b]+=frame.levels[i];levels[b]/=Math.max(1,to-from);}}}
        for(int b=0;b<3;b++){
            Band band=bands[b];band.step(levels[b],dt);
            if(band.onset&&dt>0){Ripple r=ripples[b*3+rippleCursor[b]++%3];double salt=hashSeed(seed)+rippleCursor[b]*47+b*97,a=unit(salt)*Math.PI*2,y=unit(salt+1)*2-1,rad=Math.sqrt(1-y*y);r.x=Math.cos(a)*rad;r.y=y;r.z=Math.sin(a)*rad;r.birth=animationTime;r.strength=new double[]{1.45,.9,.5}[b]*(.55+.45*band.transientLevel);r.speed=new double[]{.9,1.6,2.5}[b];r.width=new double[]{.66,.36,.22}[b];r.k=new double[]{3.4,5.5,9}[b];}
        }
        detail=bands[2].sustained;offsetGain=bands[1].sustained;centroid=(levels[1]+levels[2]*2)/Math.max(.001,levels[0]+levels[1]+levels[2])/2;
        power+=(clamp(bass,0,1)-power)*(1-Math.exp(-8*dt));double target=1+smooth((bands[0].transientLevel-.1)/.8)*.14+smooth((bands[1].transientLevel-.15)/.8)*.05;
        double remaining=dt;while(remaining>0){double step=Math.min(1./240,remaining);pulseVelocity+=((target-pulse)*150-pulseVelocity*15)*step;pulse+=pulseVelocity*step;remaining-=step;}pulse=clamp(pulse,.9,1.44);
    }
    private int palette(double phase,double d,double fine){double a=Math.pow(.5+.5*Math.cos(phase),2),b=Math.pow(.5+.5*Math.cos(phase-2.094),2),c=Math.pow(.5+.5*Math.cos(phase-4.188),2),sum=Math.max(.001,a+b+c);int base=weighted(primary,accent,muted,a/sum,b/sum,c/sum);return blend(base,blend(accent,muted,smooth((centroid-.2)/.7)),Math.min(1,smooth((d-.1)/.65)*.75+fine*.3));}
    private static int weighted(int a,int b,int c,double x,double y,double z){return Color.rgb((int)(Color.red(a)*x+Color.red(b)*y+Color.red(c)*z),(int)(Color.green(a)*x+Color.green(b)*y+Color.green(c)*z),(int)(Color.blue(a)*x+Color.blue(b)*y+Color.blue(c)*z));}
    private static int blend(int a,int b,double t){t=clamp(t,0,1);return Color.rgb((int)(Color.red(a)+(Color.red(b)-Color.red(a))*t),(int)(Color.green(a)+(Color.green(b)-Color.green(a))*t),(int)(Color.blue(a)+(Color.blue(b)-Color.blue(a))*t));}

    private static final class Ripple{double x,y,z,birth,strength,speed,width,k;}
    private static final class Band{
        double fast,floor,peak,transientLevel,sustained;boolean primed,armed,onset;
        void clear(){primed=armed=onset=false;fast=floor=peak=transientLevel=sustained=0;}
        void step(double level,double dt){
            if(!primed){fast=floor=peak=level;primed=true;}else{fast=envelope(fast,level,22,7,dt);floor=envelope(floor,level,2.5,4,dt);peak=envelope(peak,level,9,.28,dt);}
            transientLevel=clamp((fast-floor)/Math.max(.12,peak-floor),0,1);sustained=clamp(fast/Math.max(.22,peak),0,1);onset=false;
            if(!armed&&transientLevel>=.42){armed=true;onset=true;}else if(armed&&transientLevel<=.2)armed=false;
        }
    }
    private static double envelope(double current,double target,double attack,double release,double dt){return current+(target-current)*(1-Math.exp(-(target>current?attack:release)*dt));}
    private static final class LineLayout{
        final String[] glyphs;final float[] offsets;final double[] starts,ends,glows,souls;final float advance,font=96;
        LineLayout(LrcTimeline.Line line,Paint paint,double end,float tracking){
            glyphs=FoliaGlyphTimeline.split(line.text);offsets=new float[glyphs.length];starts=new double[glyphs.length];ends=new double[glyphs.length];glows=new double[glyphs.length];souls=new double[glyphs.length];float cursor=0;int chars=0;
            for(int i=0;i<glyphs.length;i++){
                offsets[i]=cursor;cursor=paint.measureText(line.text.substring(0,Math.min(line.text.length(),chars+glyphs[i].length())))+tracking*(i+1);chars+=glyphs[i].length();
                starts[i]=line.timeMs/1000.+(end-line.timeMs/1000.)*i/Math.max(1,glyphs.length);ends[i]=line.timeMs/1000.+(end-line.timeMs/1000.)*(i+1)/Math.max(1,glyphs.length);
            }
            int at=0;
            for(LrcTimeline.Word word:line.words){String[] fragments=FoliaGlyphTimeline.split(word.text);for(int g=0;g<fragments.length&&at<glyphs.length;g++,at++){starts[at]=(word.startMs+word.durationMs*g/(double)Math.max(1,fragments.length))/1000.;ends[at]=(word.startMs+word.durationMs*(g+1)/(double)Math.max(1,fragments.length))/1000.;}}
            advance=cursor-(glyphs.length>0?tracking:0);
        }
        double progress(double time){if(glyphs.length==0)return 0;double sum=0;for(int i=0;i<glyphs.length;i++)sum+=clamp((time-starts[i])/Math.max(.001,ends[i]-starts[i]),0,1);return sum/glyphs.length;}
    }

    /** Unit quaternion; the same target/reading-alignment/follower pipeline used by CameraRig.tsx. */
    private static final class Quaternion{
        double x,y,z,w=1;
        Quaternion set(Quaternion q){x=q.x;y=q.y;z=q.z;w=q.w;return this;}
        static Quaternion axis(double x,double y,double z,double angle){Quaternion q=new Quaternion();double s=Math.sin(angle/2);q.x=x*s;q.y=y*s;q.z=z*s;q.w=Math.cos(angle/2);return q;}
        Quaternion multiply(Quaternion q){double a=x,b=y,c=z,d=w;x=d*q.x+a*q.w+b*q.z-c*q.y;y=d*q.y-a*q.z+b*q.w+c*q.x;z=d*q.z+a*q.y-b*q.x+c*q.w;w=d*q.w-a*q.x-b*q.y-c*q.z;return this;}
        Quaternion slerp(Quaternion q,double t){
            double dot=x*q.x+y*q.y+z*q.z+w*q.w,sign=dot<0?-1:1;dot=Math.abs(dot);double a,b;
            if(dot>.9995){a=1-t;b=t*sign;}else{double angle=Math.acos(clamp(dot,-1,1)),sin=Math.sin(angle);a=Math.sin((1-t)*angle)/sin;b=Math.sin(t*angle)/sin*sign;}
            x=x*a+q.x*b;y=y*a+q.y*b;z=z*a+q.z*b;w=w*a+q.w*b;double length=Math.sqrt(x*x+y*y+z*z+w*w);x/=length;y/=length;z/=length;w/=length;return this;
        }
        Vec rotate(Vec v){double ix=w*v.x+y*v.z-z*v.y,iy=w*v.y+z*v.x-x*v.z,iz=w*v.z+x*v.y-y*v.x,iw=-x*v.x-y*v.y-z*v.z;return new Vec(ix*w-iw*x-iy*z+iz*y,iy*w-iw*y-iz*x+ix*z,iz*w-iw*z-ix*y+iy*x);}
        static Quaternion basis(Vec r,Vec u,Vec b){
            Quaternion q=new Quaternion();double m00=r.x,m01=u.x,m02=b.x,m10=r.y,m11=u.y,m12=b.y,m20=r.z,m21=u.z,m22=b.z,t=m00+m11+m22,s;
            if(t>0){s=.5/Math.sqrt(t+1);q.w=.25/s;q.x=(m21-m12)*s;q.y=(m02-m20)*s;q.z=(m10-m01)*s;}
            else if(m00>m11&&m00>m22){s=2*Math.sqrt(1+m00-m11-m22);q.w=(m21-m12)/s;q.x=.25*s;q.y=(m01+m10)/s;q.z=(m02+m20)/s;}
            else if(m11>m22){s=2*Math.sqrt(1+m11-m00-m22);q.w=(m02-m20)/s;q.x=(m01+m10)/s;q.y=.25*s;q.z=(m12+m21)/s;}
            else{s=2*Math.sqrt(1+m22-m00-m11);q.w=(m10-m01)/s;q.x=(m02+m20)/s;q.y=(m12+m21)/s;q.z=.25*s;}return q;
        }
    }
}
