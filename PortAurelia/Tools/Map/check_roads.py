"""Validation: no surface road may lie under the terrain (hills). Samples every road segment
across its width and compares the road height with the terrain mesh (same triangle split as
the generated terrain). Exits with code 1 if the ground covers a road anywhere.

Usage: python Tools/Map/check_roads.py [build_dir]
"""
import os
import json, math, sys

import numpy as np
B = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "Blender", "build")
d = json.load(open(B + "/plan_full.json"))
h = np.load(B + "/terrain_h.npy"); holes = np.load(B + "/terrain_holes.npy")
res, n, wmin = d["terrain_res"], d["terrain_n"], d["world_min"]
def th(x, z):
    fx=(x-wmin)/res; fz=(z-wmin)/res
    ix=min(max(int(fx),0),n-2); iz=min(max(int(fz),0),n-2)
    if holes[iz, ix]: return -999
    tx=min(max(fx-ix,0),1); tz=min(max(fz-iz,0),1)
    a,b,c,dd=h[iz,ix],h[iz,ix+1],h[iz+1,ix+1],h[iz+1,ix]
    if tx>=tz: return a+(b-a)*tx+(c-b)*tz
    return a+(c-dd)*tx+(dd-a)*tz
bad=[]; tot=0
for e in d["edges"]:
    if e["level"] != "surface": continue
    pts=e["pts"]; ys=e["ys"]; hw=e["hw"]
    for i in range(len(pts)-1):
        if e["bridge"][i] and e["bridge"][i+1]: continue
        a,b=pts[i],pts[i+1]; ax,az=a[0],a[-1]; bx,bz=b[0],b[-1]
        L=math.hypot(bx-ax,bz-az)
        if L<0.1: continue
        ux,uz=(bx-ax)/L,(bz-az)/L; nx,nz=-uz,ux
        steps=max(1,int(L/3))
        for k in range(steps+1):
            t=k/steps; x=ax+(bx-ax)*t; z=az+(bz-az)*t; y=ys[i]+(ys[i+1]-ys[i])*t
            for off in (0.0,hw*0.5,-hw*0.5,hw,-hw):
                tot+=1; g=th(x+nx*off,z+nz*off)
                if g>y+0.25: bad.append((round(x),round(z),float(g-y)))
print("road samples", tot, "covered by terrain", len(bad), "max %.2f m" % max([b[2] for b in bad], default=0.0))
sys.exit(1 if bad else 0)
