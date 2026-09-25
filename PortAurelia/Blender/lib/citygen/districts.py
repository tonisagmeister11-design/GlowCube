"""District layout of Port Aurelia.

Each district is an axis-aligned region (checked in priority order) with a visual
style that drives building generation, vegetation, lighting and traffic density.
"""

# id, name, display name, (x0, z0, x1, z1), style parameters
DISTRICTS = [
    # --- special / high priority ------------------------------------------------
    dict(id="airport", name="Aurelia International", rect=(850, -700, 1600, 640),
         ped=0.25, traffic=0.5, color=(0.78, 0.78, 0.74)),
    dict(id="harbor", name="Port Terminals", rect=(330, 640, 1600, 1150),
         ped=0.2, traffic=0.6, color=(0.62, 0.60, 0.58)),
    dict(id="park", name="Solace Park", rect=(-300, -660, 240, -450),
         ped=0.8, traffic=0.3, color=(0.45, 0.62, 0.35)),
    dict(id="financial", name="Financial District", rect=(-100, -450, 240, -50),
         ped=1.0, traffic=1.0, color=(0.55, 0.62, 0.72)),
    dict(id="downtown", name="Downtown", rect=(-300, -450, 240, 250),
         ped=1.0, traffic=1.0, color=(0.60, 0.62, 0.68)),
    dict(id="shopping", name="Galleria Row", rect=(-300, 250, 240, 560),
         ped=1.0, traffic=0.9, color=(0.72, 0.64, 0.60)),
    dict(id="marina", name="Marina Vista", rect=(-300, 560, 330, 1000),
         ped=0.6, traffic=0.6, color=(0.62, 0.70, 0.76)),
    dict(id="construction", name="Eastgate Development", rect=(330, -250, 560, -50),
         ped=0.2, traffic=0.5, color=(0.70, 0.64, 0.50)),
    dict(id="entertainment", name="Neon Quarter", rect=(330, -700, 850, -250),
         ped=0.9, traffic=0.8, color=(0.70, 0.52, 0.70)),
    dict(id="industrial", name="Ironworks", rect=(330, -250, 850, 250),
         ped=0.25, traffic=0.7, color=(0.58, 0.56, 0.52)),
    dict(id="oldtown", name="Old Town", rect=(330, 250, 850, 640),
         ped=0.9, traffic=0.7, color=(0.76, 0.62, 0.50)),
    dict(id="beach", name="Sunstrand Beach", rect=(-1600, 660, -300, 1100),
         ped=0.7, traffic=0.5, color=(0.90, 0.84, 0.64)),
    dict(id="residential", name="Palmview", rect=(-940, -700, -300, 660),
         ped=0.7, traffic=0.7, color=(0.72, 0.68, 0.60)),
    dict(id="suburbs", name="Westbrook", rect=(-1600, -700, -940, 660),
         ped=0.4, traffic=0.5, color=(0.66, 0.72, 0.56)),
    dict(id="luxury", name="Crestline Heights", rect=(-1600, -1150, -300, -700),
         ped=0.2, traffic=0.35, color=(0.62, 0.70, 0.52)),
    dict(id="rural", name="Dry Creek Valley", rect=(-1600, -1600, 1600, -1250),
         ped=0.05, traffic=0.25, color=(0.72, 0.68, 0.48)),
    dict(id="rural_east", name="Dry Creek Valley", rect=(850, -1250, 1600, -700),
         ped=0.05, traffic=0.25, color=(0.72, 0.68, 0.48)),
    dict(id="hills", name="Aurelia Hills", rect=(-300, -1250, 850, -660),
         ped=0.1, traffic=0.3, color=(0.58, 0.60, 0.44)),
]

DISTRICT_INDEX = {d["id"]: i for i, d in enumerate(DISTRICTS)}

# Districts that share a visual style
STYLE_OF = {
    "rural_east": "rural",
}


def district_at(x, z):
    for d in DISTRICTS:
        x0, z0, x1, z1 = d["rect"]
        if x0 <= x < x1 and z0 <= z < z1:
            return d["id"]
    return "sea"


def style_of(did):
    return STYLE_OF.get(did, did)


def district_info(did):
    i = DISTRICT_INDEX.get(did)
    return DISTRICTS[i] if i is not None else None
