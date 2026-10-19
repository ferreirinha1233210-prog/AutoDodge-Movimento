package br.com.experimento.autododge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Local, lightweight ONLINE classifier combined with motion tracking.
 * No pretrained Brawl Stars model is included. It learns from visually consistent
 * candidate tracks (weak, potentially wrong labels) and the user's corrections.
 * NEVER claim the candidates are guaranteed bullets.
 */
public final class ProjectileDetector {
    public static final int W=256, H=144;
    private static final int N=11; // features: bias, saturation, brightness, compactness, area, velocity, age, RGB, width/height
    private static final float[] START = { -.4f, .30f, .12f, .20f, -.25f, .55f, .45f, .0f, .0f, .0f, -.22f };
    private static final float THREAT_THRESHOLD=.56f;
    private final float[] weights=START.clone();
    private int[] previousPixels;
    private List<Spot> previousSpots=new ArrayList<>();
    private float[] lastCandidate;
    private long lastCandidateMs;
    private long lastAutoLearnMs;
    private int automaticExamples=0, userPositive=0, userNegative=0;

    public static final class Direction {
        public final float dx,dy,confidence;
        Direction(float dx,float dy,float confidence) {
            this.dx=dx;this.dy=dy;this.confidence=confidence;
        }
    }
    private static final class Spot {
        float x,y,sat,bright,r,g,b;
        int count,w,h,matched;
        float vx,vy;
        Spot(float x,float y,int count,int w,int h,float sat,float bright,float r,float g,float b) {
            this.x=x;this.y=y;this.count=count;this.w=w;this.h=h;
            this.sat=sat;this.bright=bright;this.r=r;this.g=g;this.b=b;
        }
    }
    public void reset() {
        previousPixels=null;
        previousSpots.clear();
        lastCandidate=null;
    }
    public int learnedSamples() { return automaticExamples+userPositive+userNegative; }
    public String stats() {
        return "Exemplos: "+learnedSamples()+" | corrigidos: "+(userPositive+userNegative);
    }
    public String exportModel(){
        StringBuilder s=new StringBuilder("v2;").append(automaticExamples).append(';')
                .append(userPositive).append(';').append(userNegative);
        for(float w:weights)s.append(';').append(w);
        return s.toString();
    }
    public void importModel(String data) {
        if(data==null || !data.startsWith("v2;"))return;
        try {
            String[] v=data.split(";");
            if(v.length!=N+4)return;
            float[] incoming=new float[N];
            for(int i=0;i<N;i++) {
                incoming[i]=Float.parseFloat(v[i+4]);
                if(!Float.isFinite(incoming[i]) || Math.abs(incoming[i])>5)return;
            }
            System.arraycopy(incoming,0,weights,0,N);
            automaticExamples=Math.max(0,Integer.parseInt(v[1]));
            userPositive=Math.max(0,Integer.parseInt(v[2]));
            userNegative=Math.max(0,Integer.parseInt(v[3]));
        }catch(RuntimeException ignored){}
    }
    /** An explicit label for the most recent tracked shot-like moving object. */
    public boolean feedback(boolean actuallyShot) {
        if(lastCandidate==null || System.currentTimeMillis()-lastCandidateMs>1800)return false;
        for(int i=0;i<7;i++)update(lastCandidate,actuallyShot?1f:0f,.16f);
        if(actuallyShot)userPositive++;else userNegative++;
        return true;
    }
    private static float clamp(float x,float a,float b){return Math.max(a,Math.min(b,x));}
    private float probability(float[] f){
        float z=0;
        for(int i=0;i<N;i++)z+=weights[i]*f[i];
        return (float)(1.0/(1.0+Math.exp(-clamp(z,-12f,12f))));
    }
    private void update(float[] f,float label,float rate){
        float gradient=(label-probability(f));
        for(int i=0;i<N;i++)weights[i]=clamp(weights[i]+rate*gradient*f[i],-5f,5f);
    }
    private static float[] describe(Spot s){
        float max=Math.max(s.w,s.h);
        return new float[]{1f, s.sat/255f,s.bright/255f,
            Math.min(s.w,s.h)/Math.max(1f,max),clamp(s.count/85f,0,1),
            clamp((float)Math.hypot(s.vx,s.vy)/18,0,1),
            clamp(s.matched/5f,0,1),s.r/255f,s.g/255f,s.b/255f,
            clamp(max/26f,0,1)};
    }
    public Direction analyze(int[] rgb,float px,float py){
        if(rgb==null || rgb.length!=W*H)return null;
        if(previousPixels==null){previousPixels=rgb.clone();return null;}
        boolean[] moving=new boolean[W*H];
        for(int y=14;y<H-14;y++)for(int x=16;x<W-16;x++){
            if(y>H*.64f && (x<W*.29f || x>W*.72f))continue;
            float dx=x-px,dy=y-py;
            if(dx*dx+dy*dy<10*10)continue;
            int i=y*W+x, a=rgb[i],b=previousPixels[i];
            int r=(a>>>16)&255,g=(a>>>8)&255,bl=a&255;
            int rr=(b>>>16)&255,gg=(b>>>8)&255,bb=b&255;
            int high=Math.max(r,Math.max(g,bl)), low=Math.min(r,Math.min(g,bl));
            int change=Math.abs(r-rr)+Math.abs(g-gg)+Math.abs(bl-bb);
            moving[i]=high>115 && high-low>39 && change>105;
        }
        System.arraycopy(rgb,0,previousPixels,0,rgb.length);
        boolean[] seen=new boolean[W*H];
        int[] queue=new int[W*H];
        List<Spot> spots=new ArrayList<>();
        for(int y=14;y<H-14;y++)for(int x=16;x<W-16;x++){
            int start=y*W+x;
            if(!moving[start] || seen[start])continue;
            int head=0,tail=0;
            queue[tail++]=start; seen[start]=true;
            int count=0,minX=x,maxX=x,minY=y,maxY=y;
            float sumX=0,sumY=0,sumSat=0,sumBright=0,sumR=0,sumG=0,sumB=0;
            while(head<tail){
                int index=queue[head++],cx=index%W,cy=index/W;
                int c=rgb[index],r=(c>>>16)&255,g=(c>>>8)&255,b=c&255;
                count++;sumX+=cx;sumY+=cy;
                sumSat+=Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b));
                sumBright+=Math.max(r,Math.max(g,b));sumR+=r;sumG+=g;sumB+=b;
                minX=Math.min(minX,cx);maxX=Math.max(maxX,cx);
                minY=Math.min(minY,cy);maxY=Math.max(maxY,cy);
                if(count>440)continue; // suppress massive VFX / screen transitions
                int n;
                if(cx>0){n=index-1;if(moving[n]&&!seen[n]){seen[n]=true;queue[tail++]=n;}}
                if(cx<W-1){n=index+1;if(moving[n]&&!seen[n]){seen[n]=true;queue[tail++]=n;}}
                if(cy>0){n=index-W;if(moving[n]&&!seen[n]){seen[n]=true;queue[tail++]=n;}}
                if(cy<H-1){n=index+W;if(moving[n]&&!seen[n]){seen[n]=true;queue[tail++]=n;}}
            }
            int cw=maxX-minX+1,ch=maxY-minY+1;
            if(count>=3 && count<=110 && cw<=26 && ch<=26){
                spots.add(new Spot(sumX/count,sumY/count,count,cw,ch,
                        sumSat/count,sumBright/count,sumR/count,sumG/count,sumB/count));
            }
            if(spots.size()>100)break;
        }
        Direction best=null;
        float bestScore=-99999;
        float[] example=null;
        float candidateScore=-99999f;
        int weakUpdates=0;
        for(Spot s:spots){
            Spot closest=null;
            float bestDist=23*23;
            for(Spot p:previousSpots){
                // A one-frame predictor: matching past trajectory, not just color.
                float predictX=p.x+p.vx*.65f, predictY=p.y+p.vy*.65f;
                float diffX=s.x-predictX,diffY=s.y-predictY;
                float d=diffX*diffX+diffY*diffY;
                if(d<bestDist && d>1f){bestDist=d;closest=p;}
            }
            if(closest==null)continue;
            s.vx=s.x-closest.x;s.vy=s.y-closest.y;
            float speed2=s.vx*s.vx+s.vy*s.vy;
            if(speed2<3f || speed2>23*23)continue;
            boolean stable=closest.matched>=1 && (
                closest.vx*s.vx+closest.vy*s.vy)>0.65f*
                (float)Math.sqrt((closest.vx*closest.vx+closest.vy*closest.vy)*speed2);
            s.matched=stable?Math.min(7,closest.matched+1):1;
            float targetX=px-s.x,targetY=py-s.y;
            float projected=targetX*s.vx+targetY*s.vy;
            float time=projected/speed2;
            float miss=Math.abs(targetX*s.vy-targetY*s.vx)/(float)Math.sqrt(speed2);
            float[] f=describe(s);
            float p=probability(f);
            long currentMs=System.currentTimeMillis();
            if(s.matched>=2 && weakUpdates<1 && currentMs-lastAutoLearnMs>=1200){
                // Weak labels: ONLY strong geometric evidence; not ground truth.
                // Slow auto-updates avoid overwhelming manual corrections.
                if(s.matched>=3 && time>0 && time<3.3f && miss<7 && p>.53f){
                    update(f,1,.006f);automaticExamples++;weakUpdates++;lastAutoLearnMs=currentMs;
                } else if(s.matched>=3 && (projected < -30f) && p>.40f){
                    // This weak negative is an object moving away, NOT proof it isn't a shot.
                    update(f,0,.002f);automaticExamples++;weakUpdates++;lastAutoLearnMs=currentMs;
                }
            }
            float danger=(time>0 && time<5f) ? (22-miss)*1.7f-time*2.5f+s.matched+p*8 : -100;
            if(danger>candidateScore){candidateScore=danger;example=f;}
            if(s.matched<2 || projected<=0 || time>4 || miss>18 || p<THREAT_THRESHOLD)continue;
            float speed=(float)Math.sqrt(speed2);
            float perpX=-s.vy/speed,perpY=s.vx/speed;
            float plusX=px+perpX*24,plusY=py+perpY*24;
            float minusX=px-perpX*24,minusY=py-perpY*24;
            float plusRoom=Math.min(Math.min(plusX,W-plusX),Math.min(plusY,H-plusY));
            float minusRoom=Math.min(Math.min(minusX,W-minusX),Math.min(minusY,H-minusY));
            if(minusRoom>plusRoom){perpX=-perpX;perpY=-perpY;}
            float score=danger+p*8;
            if(score>bestScore){bestScore=score;best=new Direction(perpX,perpY,p);}
        }
        if(example!=null && candidateScore>-60){
            lastCandidate=Arrays.copyOf(example,example.length);
            lastCandidateMs=System.currentTimeMillis();
        }
        previousSpots=spots;
        return best;
    }
}
