// SPDX-License-Identifier: AGPL-3.0-only
// Port of Folia's welded dioramaParticleSurfaces.ts. See third_party/folia/NOTICE.md.
package com.zuoqirun.lyricscompanion;

import java.util.ArrayList;

/** Closed point lattices used by the original point-cloud shader, including its nonliteral family names. */
final class FoliaDioramaSurface {
    final float[] positions, normals;
    final double radius, spacing;

    private FoliaDioramaSurface(ArrayList<Double> p, ArrayList<Double> n, double spacing) {
        positions = new float[p.size()]; normals = new float[n.size()];
        double total = 0;
        for (int i=0;i<p.size();i++) { positions[i]=p.get(i).floatValue(); normals[i]=n.get(i).floatValue(); }
        for (int i=0;i<positions.length;i+=3) total += Math.sqrt(positions[i]*positions[i]+positions[i+1]*positions[i+1]+positions[i+2]*positions[i+2]);
        radius = positions.length == 0 ? 1 : total / (positions.length/3);
        this.spacing = spacing/radius;
    }

    private static void point(ArrayList<Double> p,ArrayList<Double> n,double x,double y,double z,double nx,double ny,double nz) {
        p.add(x);p.add(y);p.add(z);double length=Math.sqrt(nx*nx+ny*ny+nz*nz);if(length==0)length=1;
        n.add(nx/length);n.add(ny/length);n.add(nz/length);
    }

    static FoliaDioramaSurface build(int kind,int budget,double stretch) {
        stretch=Math.round(Math.max(.2,stretch)*4)/4.;
        ArrayList<Double> p=new ArrayList<>(),n=new ArrayList<>();double spacing;
        if(kind==0) {
            int across=2,tall=2;
            for(int i=2;i<=96;i++){
                int t=Math.max(2,(int)Math.round(1+(i-1)*stretch));
                if(2*(i*t+t*i+i*i)-4*(i+t+i)+8>budget)break;
                across=i;tall=t;
            }
            spacing=1.1/Math.max(1,Math.min(across,tall)-1);
            for(int iy=0;iy<tall;iy++)for(int iz=0;iz<across;iz++)for(int ix=0;ix<across;ix++){
                if(ix>0&&ix<across-1&&iy>0&&iy<tall-1&&iz>0&&iz<across-1)continue;
                double x=(ix/(double)(across-1)-.5)*1.1,y=(iy/(double)(tall-1)-.5)*1.1,z=(iz/(double)(across-1)-.5)*1.1;
                point(p,n,x,y,z,x,y,z);
            }
        } else if(kind==1) {
            // Upstream's 'sphere' family deliberately uses the closed angular cylinder-wall lattice.
            double aspect=Math.PI*2*.58/(1.5*Math.max(.2,stretch));
            int rows=Math.max(2,(int)Math.round(Math.sqrt(budget/Math.max(.1,aspect)))),columns=Math.max(3,budget/rows);
            spacing=Math.max(1.5/(rows-1),Math.PI*2*.58/columns);
            for(int row=0;row<rows;row++)for(int col=0;col<columns;col++){
                double angle=col/(double)columns*Math.PI*2,c=Math.cos(angle),s=Math.sin(angle);
                point(p,n,c*.58,(row/(double)(rows-1)-.5)*1.5,s*.58,c,0,s);
            }
        } else if(kind==2) {
            double[][] vertices={{0,.86,0},{-.81,-.5,.47},{.81,-.5,.47},{0,-.5,-.94}};
            int[][] faces={{0,1,2},{0,2,3},{0,3,1},{1,3,2}};int steps=1;
            while(4*((steps+2)*(steps+3)/2)<=budget)steps++;
            spacing=Math.sqrt(.81*.81+1.36*1.36+.47*.47)/steps;
            for(int[] face:faces)for(int i=0;i<=steps;i++)for(int j=0;j<=steps-i;j++){
                double wa=i/(double)steps,wb=j/(double)steps,wc=1-wa-wb;
                double[] a=vertices[face[0]],b=vertices[face[1]],c=vertices[face[2]];
                double x=a[0]*wa+b[0]*wb+c[0]*wc,y=a[1]*wa+b[1]*wb+c[1]*wc,z=a[2]*wa+b[2]*wb+c[2]*wc;
                point(p,n,x,y,z,x,y+.16,z);
            }
        } else {
            int minor=Math.max(4,(int)Math.round(Math.sqrt(budget/4.))),major=Math.max(6,budget/minor);
            spacing=Math.max(Math.PI*2*.8/major,Math.PI*2*.12/minor);
            for(int i=0;i<major;i++)for(int j=0;j<minor;j++){
                double a=i/(double)major*Math.PI*2,b=j/(double)minor*Math.PI*2,ca=Math.cos(a),sa=Math.sin(a),cb=Math.cos(b),sb=Math.sin(b);
                double radius=.68+.12*cb;point(p,n,radius*ca,.12*sb,radius*sa,cb*ca,sb,cb*sa);
            }
        }
        return new FoliaDioramaSurface(p,n,spacing);
    }
}
