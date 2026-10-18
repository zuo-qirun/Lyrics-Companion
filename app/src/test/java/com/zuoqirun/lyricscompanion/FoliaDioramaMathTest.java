package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class FoliaDioramaMathTest {
    private static final String SEED = "Folia visual reference";

    @Test public void originalPathAndStagingMatchUpstreamExports() {
        FoliaDioramaMath.Frame frame = FoliaDioramaMath.path(6, SEED)[3];
        assertEquals(84688, FoliaDioramaMath.hashSeed(SEED));
        assertEquals(2.6420202052652013, frame.position.x, 1e-8);
        assertEquals(.7323789238053136, frame.position.y, 1e-8);
        assertEquals(-23.803079568655974, frame.position.z, 1e-8);
        assertEquals(.17587784446479188, frame.forward.x, 1e-8);
        assertEquals(.9841207967110007, frame.right.x, 1e-8);
        assertEquals(0, frame.right.dot(frame.forward), 1e-12);
        FoliaDioramaMath.Placement p = FoliaDioramaMath.placement(3, SEED, 1);
        assertEquals(1.2680727701313492, p.r, 1e-6);
        assertEquals(1.0731554004533086, p.u, 1e-6);
        assertEquals(.9870400636208069, p.scale, 1e-6);
        assertEquals(-.075774461311521, p.yaw, 1e-6);
    }

    @Test public void allThirteenShotOffsetsMatchOriginalAtSameClock() {
        double[][] expected = {
                {0,.6726515000000001,6.51292096}, {0,1.0259698,6.27017612},
                {1.082295826349052,.9,5.086121876662751},
                {-1.622590178209279,.62,5.512}, {0,1.2243028,5.46},
                {0,.7753263877051944,5.2}, {-.7060878458878461,1.1712728,5.76309708},
                {1.165521719392644,.4425456,5.29911512},
                {.89203608,.6565919190557007,5.304},
                {-1.38761168,.55,4.571429602788048},
                {-2.713213910613823,.9130044894134995,5.512466501748633},
                {-.7747346193143233,1.1981645851495624,5.717714801394024},
                {-.353946736,.9331516,5.5777878}
        };
        for (int i=0;i<expected.length;i++) {
            FoliaDioramaMath.Vec result=FoliaDioramaMath.shotOffset(i,.37,.63,3,SEED,1);
            assertEquals(FoliaDioramaMath.SHOTS[i],expected[i][0],result.x,1e-8);
            assertEquals(expected[i][1],result.y,1e-8);
            assertEquals(expected[i][2],result.z,1e-8);
        }
    }

    @Test public void weightedShotProgramAndFormationMatchOriginal() {
        List<LrcTimeline.Line> lines=new ArrayList<>();
        for(int i=0;i<6;i++) {
            long start=i*3000L;
            lines.add(new LrcTimeline.Line(start,3000,"line "+i,"",Arrays.asList(
                    new LrcTimeline.Word(start,1000,"a"),new LrcTimeline.Word(start+1000,1000,"b"),new LrcTimeline.Word(start+2000,1000,"c"))));
        }
        int[] expected={10,5,7,10,3,1};
        for(int i=0;i<6;i++)assertEquals(expected[i],FoliaDioramaMath.shot(i,lines,SEED));
        FoliaDioramaMath.Frame frame=FoliaDioramaMath.path(6,SEED)[3];
        List<FoliaDioramaMath.Shape> shapes=FoliaDioramaMath.formation(3,SEED,7,frame,FoliaDioramaMath.placement(3,SEED,1));
        assertEquals(7,shapes.size());
        assertEquals(8.321846213574077,shapes.get(0).position.x,1e-6);
        assertEquals(-.2492803453379723,shapes.get(0).position.y,1e-6);
        assertEquals(-23.939941335729877,shapes.get(0).position.z,1e-6);
        assertEquals(.3403283602371812,shapes.get(0).scale,1e-6);
        assertEquals(7.777297544009034,shapes.get(6).position.x,1e-6);
    }

    @Test public void originalSmoothDampCarriesVelocityAndPausesExactly() {
        double[] velocity={.3,0,0};
        double value=FoliaDioramaMath.damp(2,9,velocity,0,.42,1./60);
        assertEquals(2.0249295309591346,value,1e-12);
        assertEquals(2.6990087368772517,velocity[0],1e-12);
        double held=FoliaDioramaMath.damp(value,20,velocity,0,.42,0);
        assertEquals(value,held,1e-12);
        assertEquals(2.6990087368772517,velocity[0],1e-12);
        assertEquals(1,FoliaDioramaMath.holdSettle(3),0);
        assertEquals(0,FoliaDioramaMath.holdSettle(8),0);
    }

    @Test public void weldedSurfaceTopologyMatchesOriginalExport() {
        int[] count={488,570,544,576};
        double[] radius={.708419845777816,.7314520442634098,.610868643614877,.6853045031373461};
        double[] spacing={.17252794786970885,.1464796742085158,.18020698602347526,.15280762732515157};
        for(int i=0;i<4;i++){
            FoliaDioramaSurface s=FoliaDioramaSurface.build(i,576,1);
            assertEquals(count[i],s.positions.length/3);
            assertEquals(radius[i],s.radius,1e-6);
            assertEquals(spacing[i],s.spacing,1e-6);
        }
    }
}
