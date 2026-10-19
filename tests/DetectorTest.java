import br.com.experimento.autododge.ProjectileDetector;
import java.util.Arrays;
public class DetectorTest {
 static int[] frame(int x){
    int[] image=new int[256*144];Arrays.fill(image,0xff151515);
    if(x>=0)for(int dy=-2;dy<=2;dy++)for(int dx=-2;dx<=2;dx++){
       image[(72+dy)*256+x+dx]=0xff20aaff;
    }
    return image;
 }
 public static void main(String[] args) {
    ProjectileDetector d=new ProjectileDetector();
    String original=d.exportModel();
    if(!original.startsWith("v2;"))throw new AssertionError(original);
    d.importModel(original);
    if(!original.equals(d.exportModel()))throw new AssertionError("model roundtrip");
    if(d.analyze(frame(-1),128,72)!=null)throw new AssertionError("first frame");
    if(d.analyze(frame(-1),128,72)!=null)throw new AssertionError("still screen");
    if(d.feedback(true))throw new AssertionError("no sample to label");
    int detected=0;
    for(int x=36;x<=118;x+=6){
       if(d.analyze(frame(x),128,72)!=null)detected++;
    }
    if(detected==0)throw new AssertionError("no detection on simple approaching projectile");
    if(!d.feedback(true))throw new AssertionError("feedback not accepted");
    String trained=d.exportModel();
    ProjectileDetector restored=new ProjectileDetector();
    restored.importModel(trained);
    if(!trained.equals(restored.exportModel()))throw new AssertionError("learned model not persisted correctly");
    System.out.println("OK - static frames, moving projectile, feedback, model persistence; detections="+detected);
 }
}
