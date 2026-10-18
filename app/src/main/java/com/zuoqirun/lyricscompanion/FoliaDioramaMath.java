// SPDX-License-Identifier: AGPL-3.0-only
// Port of Folia 0.7.17 cameraPath.ts / dioramaTransition.ts. See third_party/folia/NOTICE.md.
package com.zuoqirun.lyricscompanion;

import java.util.ArrayList;
import java.util.List;

/** Original deterministic corridor, cinematic vocabulary and formation placement, free of Android APIs. */
final class FoliaDioramaMath {
    static final String[] SHOTS = {"pushIn", "pullBack", "orbit", "track", "crane", "hold",
            "swell", "spiral", "pendulum", "flyby", "arc", "float", "glide"};
    static final double HERO = 5.2, FOV = 55, STEP = 8;

    private FoliaDioramaMath() { }

    static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
    static double smooth(double t) { t = clamp(t, 0, 1); return t * t * (3 - 2 * t); }
    static double unit(double seed) { double x = Math.sin(seed * 12.9898) * 43758.5453; return x - Math.floor(x); }
    static int hashSeed(String seed) {
        if (seed == null || seed.isEmpty()) return 1;
        int hash = 0;
        for (int i = 0; i < seed.length(); i++) hash = (hash * 31 + seed.charAt(i)) % 100000;
        return hash == 0 ? 1 : hash;
    }

    static final class Vec {
        double x, y, z;
        Vec() { }
        Vec(double x, double y, double z) { set(x, y, z); }
        Vec set(double x, double y, double z) { this.x = x; this.y = y; this.z = z; return this; }
        Vec set(Vec v) { return set(v.x, v.y, v.z); }
        Vec add(Vec v) { x += v.x; y += v.y; z += v.z; return this; }
        Vec scale(double s) { x *= s; y *= s; z *= s; return this; }
        Vec normalize() { return scale(1 / Math.max(1e-12, length())); }
        double length() { return Math.sqrt(x * x + y * y + z * z); }
        double distance(Vec v) { double a=x-v.x,b=y-v.y,c=z-v.z; return Math.sqrt(a*a+b*b+c*c); }
        double dot(Vec v) { return x * v.x + y * v.y + z * v.z; }
        static Vec cross(Vec a, Vec b) { return new Vec(a.y*b.z-a.z*b.y, a.z*b.x-a.x*b.z, a.x*b.y-a.y*b.x); }
        static Vec sub(Vec a, Vec b) { return new Vec(a.x-b.x, a.y-b.y, a.z-b.z); }
    }

    static final class Frame {
        final Vec position, forward, right, up;
        Frame(Vec position, Vec forward, Vec right, Vec up) {
            this.position=position; this.forward=forward; this.right=right; this.up=up;
        }
        Vec local(double r, double u, double d) {
            return new Vec(position.x + right.x*r + up.x*u + forward.x*d,
                    position.y + right.y*r + up.y*u + forward.y*d,
                    position.z + right.z*r + up.z*u + forward.z*d);
        }
    }

    static Frame[] path(int count, String seed) {
        int n = Math.max(count, 1), base = hashSeed(seed);
        Vec[] positions = new Vec[n + 1];
        Vec pos = new Vec();
        for (int i = 0; i <= n; i++) {
            positions[i] = new Vec(pos.x, pos.y, pos.z);
            double yaw = .5 * (Math.sin(i*.23+base*.017)*.62 + Math.sin(i*.11+base*.041+1.3)*.38);
            double pitch = .32 * Math.sin(i*.17+base*.029+.7), cp = Math.cos(pitch);
            pos.x += Math.sin(yaw)*cp*STEP; pos.y += Math.sin(pitch)*STEP; pos.z -= Math.cos(yaw)*cp*STEP;
        }
        Frame[] frames = new Frame[n];
        for (int i=0;i<n;i++) {
            Vec forward = Vec.sub(positions[i+1],positions[i]).normalize();
            Vec right = Vec.cross(forward,new Vec(0,1,0)).normalize();
            frames[i] = new Frame(positions[i],forward,right,Vec.cross(right,forward).normalize());
        }
        return frames;
    }

    static Frame frameAt(Frame[] frames, int index) {
        int clamped = Math.max(0, Math.min(frames.length - 1, index));
        Frame f = frames[clamped];
        return clamped == index ? f : new Frame(f.local(0,0,(index-clamped)*STEP),f.forward,f.right,f.up);
    }

    static final class Placement {
        final double r,u,scale,roll,yaw,look;
        Placement(double r,double u,double scale,double roll,double yaw,double look) {
            this.r=r;this.u=u;this.scale=scale;this.roll=roll;this.yaw=yaw;this.look=look;
        }
    }
    static Placement placement(int index,String seed,double weave) {
        double base=hashSeed(seed),i=Math.max(index,0);
        double jr=(unit(base+i*11+2)-.5)*.6,ju=(unit(base+i*11+4)-.5)*.6;
        return new Placement((Math.sin(i*.47+base*.019)*.7+jr)*1.8*weave,
                (Math.sin(i*.29+base*.027+.9)*.7+ju)*1.2*weave,
                .82+unit(base+i*11+5)*.46,(unit(base+i*11+6)-.5)*.4*weave,
                (unit(base+i*11+7)-.5)*.32*weave,(unit(base+i*11+8)-.5)*2.2*weave);
    }

    /** Android's lyric model has no section/chorus metadata; their original weights remain neutral. */
    private static int baseShot(int index,List<LrcTimeline.Line> lines,String seed) {
        if(index<0||index>=lines.size()) return 5;
        LrcTimeline.Line line=lines.get(index);
        double[] w={1,1,1,1,1,1,.85,.9,1,.9,1,.9,1};
        w[3]+=1.1;w[4]+=.9;w[5]+=.9;w[8]+=.8;w[11]+=.8;w[12]+=.6;w[10]+=.4;
        int count=line.words.size(),notes=0;double max=0,total=0;
        for(LrcTimeline.Word word:line.words) if(word.durationMs>0){double d=word.durationMs/1000.;max=Math.max(max,d);total+=d;notes++;}
        if(count>=8){w[3]+=1.6;w[1]+=1;w[2]-=.4;w[0]-=.3;}
        else if(count<=3){w[0]+=1.4;w[2]+=.9;w[5]+=.5;w[3]-=.8;}
        if(max>=1.3){w[6]+=2.4;w[7]+=1;w[11]+=1.2;w[8]+=.6;w[2]+=.8;w[10]+=.6;w[5]+=.3;w[3]-=.5;}
        if(notes>=4&&total/notes<=.34){w[3]+=1.4;w[9]+=1.5;w[0]+=.8;w[6]-=1.6;w[5]-=.8;w[4]-=.4;}
        double sum=0;for(int i=0;i<w.length;i++){w[i]=Math.max(.05,w[i]);sum+=w[i];}
        double pick=unit(hashSeed(seed)+index*101+7)*sum;
        for(int i=0;i<w.length;i++){pick-=w[i];if(pick<=0)return i;}return 5;
    }
    static int shot(int index,List<LrcTimeline.Line> lines,String seed) {
        int pick=baseShot(index,lines,seed),p1=index>0?baseShot(index-1,lines,seed):-1,p2=index>1?baseShot(index-2,lines,seed):-1;
        if(pick!=p1&&pick!=p2)return pick;
        int count=0;for(int i=0;i<SHOTS.length;i++)if(i!=pick&&i!=p1&&i!=p2)count++;
        int select=(int)(unit(hashSeed(seed)+index*53+19)*count)%count;
        for(int i=0;i<SHOTS.length;i++)if(i!=pick&&i!=p1&&i!=p2&&select--==0)return i;
        return 5;
    }
    static int direction(int index,String seed) {return unit(hashSeed(seed)+index*7+3)<.5?-1:1;}

    /** Offset is (right, up, back), in the active lyric frame. All thirteen upstream moves are retained. */
    static Vec shotOffset(int kind,double progress,double wordProgress,int index,String seed,double move) {
        double e=smooth(progress),dir=direction(index,seed),r=0,u=.5,b=HERO;
        switch(kind){
            case 0:b=HERO*(1.5-.8*e);u=.5*(1.2-.5*e)+.15;break;
            case 1:b=HERO*(.85+1.15*e);u=.5+1.7*e;break;
            case 2:{double a=dir*.55*(2*e-1);r=HERO*Math.sin(a);b=HERO*Math.cos(a);u=.9;break;}
            case 3:r=dir*HERO*.34*Math.sin(Math.PI*clamp(wordProgress,0,1));b=HERO*1.06;u=.62;break;
            case 4:{double from=dir>0?-1.5:2.4,to=dir>0?2.4:-1.4;u=from+(to-from)*e;b=HERO*1.05;break;}
            case 6:{double a=dir*.4*e;r=HERO*Math.sin(a)*1.1;b=HERO*(1+.35*e);u=.8+1.2*e;break;}
            case 7:{double a=dir*.7*(2*e-1);r=HERO*Math.sin(a)*.85;b=HERO*(1.05-.1*e);u=-.3+2.4*e;break;}
            case 8:r=dir*HERO*.45*(2*e-1);u=1.4-.9*Math.sin(Math.PI*e);b=HERO*1.02;break;
            case 9:r=dir*HERO*(.7-1.4*e);b=HERO*(.78+.12*Math.sin(Math.PI*e));u=.55;break;
            case 10:{double a=dir*.5*Math.sin(Math.PI*e);r=HERO*Math.sin(a)*1.3;b=HERO*(1.05+.12*(1-Math.cos(a)));u=.5+.5*Math.sin(Math.PI*e);break;}
            case 11:r=dir*HERO*.16*Math.sin(e*Math.PI*2);u=1.1+.5*Math.sin(e*Math.PI*2+1);b=HERO*(1.05+.06*Math.sin(e*Math.PI));break;
            case 12:r=dir*HERO*.22*e;u=.5+1.4*e;b=HERO*(1.15-.25*e);break;
            default:u=.5+.3*Math.sin(progress*Math.PI);break;
        }
        return new Vec(r*move,.5+(u-.5)*move,HERO+(b-HERO)*move);
    }

    static double holdSettle(double held){return 1-smooth((held-3)/5);}
    static double truck(double progress,double width,double half){
        if(width<=0)return 0;double allowed=Math.max(Math.abs(half-width/2),.001);
        return allowed*Math.tanh((clamp(progress,0,1)-.5)*width/allowed);
    }
    static Vec drift(double time,String seed,double scale){
        double ph=hashSeed(seed)*.017;
        return new Vec((Math.sin(time*.11+ph)*.6+Math.sin(time*.047+ph*1.7)*.4)*.4*scale,
                Math.sin(time*.09+ph*.7)*.2*scale+(Math.sin(time*.13+ph*1.3)*.6+Math.sin(time*.061+ph*2.1)*.4)*.4*scale,
                Math.sin(time*.05+ph*1.1)*.5*scale);
    }
    static double damp(double current,double target,double[] velocity,int axis,double smoothTime,double dt){
        double omega=2/Math.max(.0001,smoothTime),x=omega*dt,exp=1/(1+x+.48*x*x+.235*x*x*x);
        double change=current-target,temp=(velocity[axis]+omega*change)*dt;
        velocity[axis]=(velocity[axis]-omega*temp)*exp;return target+(change+temp)*exp;
    }
    static double life(double distance){return smooth((27-distance)/7)*smooth((distance-1.2)/1.8);}
    static double textLife(double distance){return smooth((40-distance)/8)*smooth((distance-.9)/1.1);}
    static double frameFit(double width,double aspect){return width<=0?1:Math.min(1,Math.max(.28,2*HERO*Math.tan(Math.toRadians(FOV)/2)*aspect*.72/width));}
    static Vec transitionOffset(String seed,int epoch){
        double base=hashSeed(seed)+epoch*131,az=unit(base+1)*Math.PI*2,el=(unit(base+2)-.35)*1.3,ce=Math.cos(el);
        return new Vec(Math.sin(az)*ce,Math.sin(el),-Math.abs(Math.cos(az)*ce)-.35).normalize().scale(46);
    }

    static final class Shape {
        final int kind, color; final double scale,stretch,spin; final Vec position; final boolean far;
        Shape(int kind,int color,double scale,double stretch,double spin,Vec position,boolean far){
            this.kind=kind;this.color=color;this.scale=scale;this.stretch=stretch;this.spin=spin;this.position=position;this.far=far;
        }
    }
    private static final class Spec {
        int kind,color;double r,u,d,s,t;
        Spec(int kind,double r,double u,double d,double scale,double stretch,int color){this.kind=kind;this.r=r;this.u=u;this.d=d;s=scale;t=stretch;this.color=color;}
        double radius(){return s*Math.max(kind==0?.5:kind==1?.74:kind==2?.68:.9,t*.55);}
    }
    private static void add(List<Spec> list,int k,double r,double u,double d,double s,double t,int color){list.add(new Spec(k,r,u,d,s,t,color));}
    private static void pair(List<Spec> list,int k,double r,double u,double d,double s,double t,int color){add(list,k,-Math.abs(r),u,d,s,t,color);add(list,k,Math.abs(r),u,d,s,t,color);}
    private static double rnd(double salt,double k){return unit(salt+k);}
    static List<Shape> formation(int index,String seed,int shot,Frame frame,Placement p){
        double salt=hashSeed(seed)+index*97,dir=direction(index,seed);List<Spec> a=new ArrayList<>();
        switch(shot){
            case 2:{double radius=4.4+rnd(salt,1)*.6;for(int j=0;j<6;j++){double angle=j/6.*Math.PI*2+rnd(salt,2)*.6;add(a,j==0?3:1,Math.cos(angle)*radius,Math.sin(angle)*radius,.6+rnd(salt,10+j)*1.4,.42+rnd(salt,20+j)*.32,1,-1);}break;}
            case 0:{double gate=4.3+rnd(salt,1)*.5;for(int i=0;i<2;i++){double side=i==0?-1:1;add(a,0,side*gate,-.6,.5+rnd(salt,i)*.5,.55+rnd(salt,2+i)*.2,2.8+rnd(salt,6+i)*.8,-1);add(a,0,side*(gate+1.6),.6,4.5+rnd(salt,3+i)*2,.7+rnd(salt,4+i)*.3,3.4,-1);}break;}
            case 3:for(int i=0;i<2;i++)for(int j=0;j<3;j++)add(a,0,(i==0?-1:1)*(4.2+rnd(salt,i*5+j)*.4),-.4+(rnd(salt,i*7+j)-.5)*.6,.5+j*2.4,.5+rnd(salt,i*3+j)*.15,2.6+rnd(salt,i+j)*.6,-1);break;
            case 4:{double radius=3.6+rnd(salt,1)*.5;for(int j=0;j<5;j++){double angle=Math.PI*(.15+.7*j/4.);add(a,0,Math.cos(angle)*radius,Math.abs(Math.sin(angle))*radius+1.2,.6+rnd(salt,30+j)*.8,.45+rnd(salt,40+j)*.25,1.4,-1);}break;}
            case 6:{double lat=4.2+rnd(salt,2)*.5;for(int j=0;j<2;j++)pair(a,0,lat,-1.5+j*2.1,1+j*1.2,.48-j*.06,1.45,j%2);pair(a,1,lat,2.65,3.4,.5,1,0);break;}
            case 7:for(int j=0;j<7;j++){double f=j/6.,angle=dir*f*Math.PI*2.2+rnd(salt,1)*.5;add(a,1,Math.cos(angle)*4.3,-2.2+f*5.2,.5+f*2.2,.32+rnd(salt,20+j)*.2,1,-1);}break;
            case 8:for(int j=0;j<4;j++)add(a,0,-3.9+j*2.6+(rnd(salt,j)-.5)*.5,3+rnd(salt,j*2)*1.2,.5+rnd(salt,j*3)*1.2,.3+rnd(salt,j*4)*.12,2.2+rnd(salt,j*5)*.8,-1);break;
            case 9:for(int j=0;j<3;j++)pair(a,0,4.25+rnd(salt,j)*.25,-.45+j*.45,1+j*2.1,.42+rnd(salt,j*3)*.1,2.2,j%2);break;
            case 1:pair(a,1,5.8+rnd(salt,9)*.5,1.25,6.8+rnd(salt,11),1.25+rnd(salt,12)*.25,1,0);for(int j=0;j<2;j++)pair(a,j==0?0:2,4.4+j*1.15,-1.4+j*2.2,2.2+j*2.1,.58+rnd(salt,j*4)*.18,j==0?1.5:1,1);break;
            case 10:{double radius=5.2+rnd(salt,1)*.6;for(int j=0;j<5;j++){double angle=(-.5+j/4.)*1.5*dir;add(a,j==2?3:1,Math.sin(angle)*radius,.3+Math.cos(angle)*.7,1+rnd(salt,20+j)*1.6,.45+rnd(salt,30+j)*.32,1,-1);}break;}
            case 11:for(int j=0;j<3;j++)pair(a,1,4.1+j*.85,-1.2+j*1.35,1.2+j*1.55,.42+rnd(salt,j*4)*.22,1,j%2);break;
            case 12:for(int j=0;j<3;j++)pair(a,0,4.25+rnd(salt,j)*.3,-1.5+j*1.7,1+j*1.7,.46+rnd(salt,j*2)*.12,1.85,j%2);break;
            default:pair(a,3,5.6,1.55,7.2+rnd(salt,1),1.05+rnd(salt,2)*.2,1,0);pair(a,1,4.45,-1.1,2.1+rnd(salt,4),.58+rnd(salt,5)*.16,1,1);break;
        }
        rail(a);clearText(a);separate(a);rail(a);clearText(a);
        List<Shape> out=new ArrayList<>(a.size());
        for(int j=0;j<a.size();j++){Spec s=a.get(j);out.add(new Shape(s.kind,s.color<0?j%2:s.color,s.s,s.t,.04+rnd(salt,j*13+5)*.1,frame.local(p.r+s.r,p.u+s.u,s.d),s.d>3.5));}
        return out;
    }
    private static void rail(List<Spec>a){for(int i=0;i<a.size();i++){Spec s=a.get(i);double min=2.8+s.radius()*1.44,d=Math.hypot(s.r,s.u);if(d>=min)continue;double angle=(i%2==0?1:-1)*Math.PI*.16;s.r=(d>.001?s.r/d:Math.cos(angle))*min;s.u=(d>.001?s.u/d:Math.sin(angle))*min;}}
    private static void clearText(List<Spec>a){for(int i=0;i<a.size();i++){Spec s=a.get(i);double r=s.radius()*1.44;if(Math.abs(s.u)-r<1.4&&Math.abs(s.r)-r<5.2)s.r=(s.r==0?(i%2==0?1:-1):Math.signum(s.r))*(5.2+r+.35);}}
    private static void separate(List<Spec>a){
        for(int n=0;n<6;n++)for(int i=0;i<a.size();i++)for(int j=i+1;j<a.size();j++){
            Spec x=a.get(i),y=a.get(j);double min=(x.radius()+y.radius())*1.3,dr=y.r-x.r,du=y.u-x.u,dd=y.d-x.d,d=Math.sqrt(dr*dr+du*du+dd*dd);
            if(d>=min)continue;if(d<.001){dr=1;du=.5;dd=.25;d=Math.sqrt(1.3125);}double push=(min-d)/2/d;
            x.r-=dr*push;x.u-=du*push;x.d-=dd*push;y.r+=dr*push;y.u+=du*push;y.d+=dd*push;
        }
        for(Spec s:a)s.d=Math.max(s.d,1);
    }
}
