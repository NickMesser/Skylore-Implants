"""Implant body-model source.

Each implant is designed once, for the right side where it has one, as cubes in
Blockbench player space (feet at y=0, front faces -z, right limbs at -x). For every cube
face this script paints its own pixel-exact patch into the implant's atlas (1 texel per
model unit, like a player skin) plus a glow mask, mirrors the design onto the other
side, and writes:

  common/.../textures/implant/<id>.png        colour atlas
  common/.../textures/implant/<id>_glow.png   full-bright pixels (absent if none glow)
  common/.../implant_models/<id>.json         quads per body slot, in ModelPart space
  build/<name>.js                             Blockbench build script for the MCP

    python implant_art.py                      every implant: textures + game models
    python implant_art.py cyber_arm            one implant (+ build/cyber_arm.js)
    python implant_art.py --scene a b:l_arm    build/scene.js with those worn on one body
"""
import json, pathlib, sys
from PIL import Image

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parent.parent
ASSETS = ROOT / 'common/src/main/resources/assets/augment'
TEXTURES = ASSETS / 'textures/implant'
MODELS = ASSETS / 'implant_models'
BUILD = HERE / 'build'

# Player bones in Blockbench space. Pivots match the vanilla HumanoidModel parts, so the
# exported quads ride those ModelParts directly.
BONES = {
    'head': [0, 24, 0], 'body': [0, 24, 0],
    'right_arm': [-5, 22, 0], 'left_arm': [5, 22, 0],
    'right_leg': [-1.9, 12, 0], 'left_leg': [1.9, 12, 0],
}

# Bare limb boxes, and how far an implant may reach beyond them. The ceiling keeps each
# implant inside the vanilla armour shell (helmet, chestplate and boots are inflated by
# 1.0, leggings by 0.5) with a 0.1 gap so the two never z-fight. Cubes may also sink into
# the limb; only the outside is limited. Skin overlays sit at 0.25 (0.5 for the hat), so
# visible surfaces should clear those. Hands are the bottom 1.4 of the arm; arm implants
# start above y=13.4 so both can be worn together.
LIMBS = {
    'head': ([-4, 24, -4], [4, 32, 4], 0.9),
    'body': ([-4, 12, -2], [4, 24, 2], 0.9),
    'right_arm': ([-8, 12, -2], [-4, 24, 2], 0.9),
    'left_arm': ([4, 12, -2], [8, 24, 2], 0.9),
    'right_leg': ([-4, 0, -2], [0, 12, 2], 0.4),
    'left_leg': ([0, 0, -2], [4, 12, 2], 0.4),
}
EXPOSED = set()  # implant ids allowed to poke out of the armour shell

# Which design kind and side each body slot takes.
SLOTS = {
    'head': ('head', None), 'torso': ('torso', None),
    'r_eye': ('eye', 'right'), 'l_eye': ('eye', 'left'),
    'r_hand': ('hand', 'right'), 'l_hand': ('hand', 'left'),
    'r_arm': ('arm', 'right'), 'l_arm': ('arm', 'left'),
    'r_leg': ('leg', 'right'), 'l_leg': ('leg', 'left'),
}
KIND_BONE = {'head': 'head', 'eye': 'head', 'torso': 'body', 'hand': 'right_arm', 'arm': 'right_arm', 'leg': 'right_leg'}

# Palette lifted from the item icons.
CLEAR = (0, 0, 0, 0)
K = (0, 0, 0, 255)
N0, N1, N2, N3 = (34, 42, 92, 255), (55, 68, 110, 255), (86, 106, 137, 255), (139, 171, 191, 255)
M0, M1, M2 = (128, 12, 83, 255), (171, 31, 101, 255), (230, 51, 106, 255)
B0, B1, B2 = (104, 60, 52, 255), (163, 109, 62, 255), (204, 172, 104, 255)
G0, G1, G2 = (21, 137, 104, 255), (70, 198, 87, 255), (160, 240, 150, 255)
C0, C1, C2 = (21, 137, 104, 255), (91, 236, 241, 255), (140, 255, 222, 255)
Y0, Y1, Y2 = (255, 129, 66, 255), (255, 218, 69, 255), (255, 240, 160, 255)
W0, W1, W2 = (139, 171, 191, 255), (204, 226, 225, 255), (255, 255, 255, 255)
R0, R1, R2 = (128, 20, 24, 255), (210, 47, 30, 255), (255, 129, 66, 255)
U0, U1, U2 = (38, 72, 160, 255), (60, 110, 220, 255), (120, 180, 255, 255)
PC0, PC1 = (18, 80, 56, 255), (36, 128, 78, 255)
P1 = (249, 107, 128, 255)
DK = (52, 30, 26, 255)

# Pixels of these colours glow (drawn again full-bright) when their material is in GLOWING.
GLOW_COLOURS = {C1, C2, M2, P1, G1, G2, R1, R2, U1, U2, Y1, Y2, W2}
GLOWING = {'glow_cyan', 'glow_magenta', 'glow_red', 'glow_blue', 'leds', 'hyper_lens', 'optic_lens',
           'retina_lens', 'eyeball_lens', 'hud', 'screen', 'ear_grille', 'hydraulic', 'nano_mesh',
           'heart', 'capsule', 'crystal', 'converter'}


def bevel(hi, mid, lo, rivet=None):
    """Raised plate: lit top/left edge, shaded bottom/right edge, optional corner rivets."""
    def paint(x, y, w, h):
        if rivet and w >= 5 and h >= 5 and (x, y) in {(1, 1), (w - 2, 1), (1, h - 2), (w - 2, h - 2)}: return rivet
        if y == 0 or x == 0: return hi
        if y == h - 1 or x == w - 1: return lo
        return mid
    return paint

def seamed(hi, mid, lo, edge):
    def paint(x, y, w, h):
        if y == 0: return hi
        if y == h - 1 or x == w - 1: return edge
        if h >= 4 and y % 3 == 2: return lo
        return mid
    return paint

def tube(lo, mid, hi, band=N0):
    """Round tube along its long axis, with a clamp band every 4 texels."""
    def paint(x, y, w, h):
        along, across, n = (y, x, w) if h >= w else (x, y, h)
        if band and along % 4 == 3: return band
        if n <= 2: return hi if across == 0 else mid
        d = abs(across - (n - 1) / 2)
        return hi if d < 0.6 else mid if d < n / 2 - 0.5 else lo
    return paint

def ribbed(x, y, w, h):
    if x == 0 or x == w - 1: return N1
    return K if y % 2 else N0

def fins(x, y, w, h):
    """Vertical cooling fins."""
    if y == 0: return N3
    if y == h - 1: return N0
    return N2 if x % 2 == 0 else N0

def vent(x, y, w, h):
    if x == 0 or y == 0 or x == w - 1 or y == h - 1: return N1
    return K if y % 2 else N0

def leds(on, off, frame):
    """Indicator stack: lit cells separated by dark frame rows."""
    def paint(x, y, w, h):
        if w >= 3 and (x == 0 or x == w - 1): return frame
        return frame if y % 2 == 0 else on if y < h * 0.8 else off
    return paint

def glow(core, edge):
    def paint(x, y, w, h):
        # Patches too small for a rim are all core, so thin light strips still glow.
        if w > 2 and h > 2 and (x in (0, w - 1) or y in (0, h - 1)): return edge
        return core
    return paint

def lines(base, line, edge, step=2):
    """Screen or hologram: horizontal scan lines inside a frame."""
    def paint(x, y, w, h):
        if x in (0, w - 1) or y in (0, h - 1): return edge
        return line if y % step == 1 else base
    return paint

def weave(x, y, w, h):
    """Woven synthetic muscle: navy fibres with magenta seams."""
    if y == 0: return N2
    if y == h - 1: return N0
    if (x + y) % 4 == 0 or (x - y) % 4 == 0: return M1
    return N1

def pcb(x, y, w, h):
    if x in (0, w - 1) or y in (0, h - 1): return PC0
    if y % 3 == 1 and x % 4 != 3: return B2
    if x % 4 == 2: return B1
    return PC1

def brain(x, y, w, h):
    """Folded cortex in the Cyber Brain's reds and oranges."""
    if (x * 2 + y * 3) % 7 < 2: return R2
    if (x + y * 2) % 5 == 0: return R0
    return R1

def mesh(x, y, w, h):
    """Nano skin: navy weave with a cyan circuit grid."""
    if x % 3 == 1 and y % 3 == 1: return C2
    if x % 3 == 1 or y % 3 == 1: return C1
    return N1

def gear(x, y, w, h):
    cx, cy = (w - 1) / 2, (h - 1) / 2
    d = max(abs(x - cx), abs(y - cy))
    if d < 0.8: return DK
    if d < 1.6: return B2
    if (x + y) % 2 == 0 and (x in (0, w - 1) or y in (0, h - 1)): return B0
    return B1

def sprite(*rows):
    """Hand-drawn face art, one letter per texel (see INK). Faces of another size sample it nearest-neighbour."""
    def paint(x, y, w, h):
        row = rows[min(len(rows) - 1, y * len(rows) // h)]
        return INK[row[min(len(row) - 1, x * len(row) // w)]]
    return paint

INK = {'k': K, 'n': N0, 'N': N1, 's': N2, 'S': N3, 'm': M0, 'M': M1, 'p': M2, 'P': P1,
       'w': W1, 'W': W2, 'c': C1, 'C': C2, 'r': R0, 'R': R1, 'o': R2, 'u': U0, 'U': U1, 'v': U2,
       'b': B0, 'B': B1, 'y': B2, 'g': G1, 'G': G2, '.': CLEAR}

def hydraulic(x, y, w, h):
    """Glass cylinder with pink fluid filling the lower two thirds."""
    if y == 0 or y == h - 1: return W0
    if x == 0: return W2
    if x == w - 1: return W0
    return (P1 if x == 1 else M2) if y > h / 3 else W1

MATERIALS = {
    'plate': bevel(N2, N1, N0, N3), 'panel': seamed(N3, N2, N0, N1), 'joint': ribbed, 'vent': vent,
    'fins': fins, 'cable': tube(M0, M1, M2), 'cable_green': tube(PC0, G0, G1, None),
    'cable_blue': tube(U0, U1, U2, None), 'brass': tube(B0, B1, B2, None), 'brass_plate': bevel(B2, B1, B0, DK),
    'chrome': tube(N2, N3, W1, None), 'steel': bevel(W1, N3, N2), 'yellow': bevel(Y2, Y1, Y0),
    'leds': leds(G1, G0, N0), 'glow_cyan': glow(C2, C0), 'glow_magenta': glow(M2, M0),
    'glow_red': glow(R2, R0), 'glow_blue': glow(U2, U0),
    'hud': lines(C1, C2, C0), 'screen': lines(U1, U2, N0), 'hydraulic': hydraulic,
    'weave': weave, 'pcb': pcb, 'brain': brain, 'nano_mesh': mesh, 'gear': gear,
    'hyper_lens': sprite('MpM', 'nWp', 'MpM'), 'optic_lens': sprite('sws', 'cCw', 'sSs'),
    'retina_lens': sprite('NsN', 'sRs', 'NnN'), 'eyeball_lens': sprite('wWw', 'UkU', 'wwS'),
    'dark_lens': sprite('MpM', 'nkn', 'MnM'),
    'ear_grille': sprite('NsN', 'nNn', 'RoR', 'nNn', 'NnN'),
    'jaw_grille': sprite('SwSwSwS', 'nkNkNkn', 'NsNsNsN'),
    'heart': sprite('.RR.RR', 'RoRRRR', 'RRRRpR', '.RRRR.', '..RR..'),
    'lung': sprite('yBBy', 'BybB', 'BBbB', 'bBBb'),
    'capsule': sprite('SwS', 'sUs', 'svs', 'SsS', 'sSs'),
    'crystal': sprite('CwC', 'cCc', 'CcC', 'cCc'),
    'converter': sprite('NnnN', 'nppn', 'nMMn', 'NnnN'),
}

# name: {kind: [(part, from, to, material, {face: material override})]}
# Every design sits on the right side (the -x limb, or the right eye) where it has one.
IMPLANTS = {
    # ---------------------------------------------------------------- arms (box x -8..-4, y 12..24)
    'cyber_arm': {'arm': [
        ('shoulder_pad',   [-8.7, 21.4, -2.7], [-3.7, 24.6, 2.7], 'plate', {}),
        ('shoulder_trim',  [-8.9, 20.8, -2.9], [-7.6, 22.6, 2.9], 'panel', {}),
        ('upper_sleeve',   [-8.4, 17.4, -2.4], [-3.6, 21.4, 2.4], 'panel', {}),
        ('elbow_joint',    [-8.3, 16.2, -2.3], [-3.7, 17.4, 2.3], 'joint', {}),
        ('forearm_casing', [-8.6, 14.0, -2.6], [-3.4, 16.2, 2.6], 'plate', {'west': 'vent'}),
        ('wrist_ring',     [-8.35, 13.4, -2.35], [-3.65, 14.0, 2.35], 'joint', {}),
        ('front_cable',    [-6.6, 14.2, -2.9], [-5.4, 20.4, -2.4], 'cable', {}),
        ('outer_cable',    [-8.9, 15.4, 1.0], [-8.4, 21.4, 1.8], 'cable', {}),
        ('rear_piston',    [-6.9, 14.6, 2.4], [-5.9, 19.6, 2.9], 'brass', {}),
        ('outer_piston',   [-8.9, 14.6, -1.4], [-8.4, 19.6, -0.4], 'brass', {}),
    ]},
    # Exoskeleton frame: shoulder ring, outer strut, brass elbow hub and magenta plasma lines.
    'plasma_pulse_arm': {'arm': [
        ('shoulder_ring', [-8.4, 21.4, -2.45], [-3.6, 23.9, 2.45], 'plate', {}),
        ('outer_strut',   [-8.8, 14.4, -0.6], [-8.35, 21.4, 0.6], 'panel', {}),
        ('elbow_hub',     [-8.9, 16.2, -1.3], [-8.3, 18.4, 1.3], 'gear', {}),
        ('front_strut',   [-6.6, 14.4, -2.85], [-5.4, 21.4, -2.35], 'panel', {}),
        ('plasma_line',   [-5.0, 15.0, -2.9], [-4.3, 21.0, -2.4], 'cable', {}),
        ('forearm_band',  [-8.4, 14.0, -2.45], [-3.6, 15.4, 2.45], 'joint', {}),
        ('emitter',       [-7.9, 14.1, -2.9], [-6.9, 15.2, -2.45], 'glow_magenta', {}),
    ]},
    # Forearm gauntlet with a cyan hologram screen on the outside and a brass frame.
    'holo_display_arm': {'arm': [
        ('gauntlet',   [-8.5, 13.6, -2.5], [-3.5, 17.4, 2.5], 'plate', {}),
        ('screen',     [-8.9, 14.1, -1.9], [-8.5, 17.0, 1.9], 'hud', {}),
        ('frame_top',  [-8.8, 17.4, -2.2], [-8.3, 18.0, 2.2], 'brass', {}),
        ('projector',  [-8.8, 18.0, -0.6], [-8.3, 19.4, 0.6], 'brass_plate', {'west': 'glow_cyan'}),
        ('upper_band', [-8.35, 20.6, -2.35], [-3.65, 21.6, 2.35], 'joint', {}),
        ('feed',       [-8.75, 19.4, 0.8], [-8.35, 20.6, 1.4], 'cable', {}),
    ]},
    # Servo motors at shoulder and elbow joined by brass links.
    'servo_motor_arm': {'arm': [
        ('shoulder_servo', [-8.9, 20.0, -1.7], [-8.3, 23.4, 1.7], 'gear', {}),
        ('shoulder_band',  [-8.4, 21.0, -2.4], [-3.6, 22.4, 2.4], 'plate', {}),
        ('upper_link',     [-8.7, 18.4, -0.5], [-8.3, 20.0, 0.5], 'brass', {}),
        ('elbow_servo',    [-8.9, 15.8, -1.5], [-8.3, 18.4, 1.5], 'gear', {}),
        ('forearm_band',   [-8.4, 14.0, -2.4], [-3.6, 15.4, 2.4], 'plate', {}),
        ('front_rod',      [-6.4, 14.4, -2.8], [-5.6, 21.6, -2.4], 'brass', {}),
        ('drive_belt',     [-8.8, 15.4, 1.6], [-8.3, 21.2, 2.2], 'joint', {}),
    ]},
    # ---------------------------------------------------------------- arms and legs (leg box x -4..0, y 0..12)
    'hydraulic_limbs': {
        'arm': [
            ('cylinder',   [-8.9, 14.8, -1.2], [-8.1, 20.2, 1.2], 'hydraulic', {}),
            ('mount_top',  [-8.9, 20.2, -1.6], [-8.0, 21.6, 1.6], 'plate', {}),
            ('mount_low',  [-8.9, 13.6, -1.6], [-8.0, 14.8, 1.6], 'plate', {}),
            ('elbow_ring', [-8.35, 16.6, -2.35], [-3.65, 17.8, 2.35], 'joint', {}),
            ('hose',       [-6.6, 14.4, -2.8], [-6.0, 20.6, -2.3], 'cable', {}),
        ],
        'leg': [
            ('cylinder',  [-4.4, 2.2, -1.2], [-3.6, 9.8, 1.2], 'hydraulic', {}),
            ('mount_top', [-4.4, 9.8, -1.6], [-3.5, 11.2, 1.6], 'plate', {}),
            ('mount_low', [-4.4, 1.0, -1.6], [-3.5, 2.2, 1.6], 'plate', {}),
            ('knee_ring', [-3.6, 5.2, -2.4], [-0.4, 6.6, -1.6], 'joint', {}),
            ('hose',      [-2.6, 2.4, -2.4], [-2.0, 9.0, -1.8], 'cable', {}),
        ]},
    # Brass tendon rods anchored by bands, front and back.
    'mech_tendons': {
        'arm': [
            ('top_band',     [-8.4, 22.4, -2.4], [-3.6, 23.4, 2.4], 'joint', {}),
            ('low_band',     [-8.4, 13.6, -2.4], [-3.6, 14.6, 2.4], 'joint', {}),
            ('front_outer',  [-7.4, 14.6, -2.7], [-6.8, 22.4, -2.2], 'brass', {}),
            ('front_inner',  [-5.2, 14.6, -2.7], [-4.6, 22.4, -2.2], 'brass', {}),
            ('back_outer',   [-7.4, 14.6, 2.2], [-6.8, 22.4, 2.7], 'brass', {}),
            ('back_inner',   [-5.2, 14.6, 2.2], [-4.6, 22.4, 2.7], 'brass', {}),
            ('side_tendon',  [-8.7, 14.6, -0.3], [-8.2, 22.4, 0.3], 'brass', {}),
            ('elbow_pulley', [-8.8, 17.0, -0.8], [-8.3, 18.6, 0.8], 'gear', {}),
        ],
        'leg': [
            ('top_band',    [-4.35, 10.4, -2.35], [0.35, 11.4, 2.35], 'joint', {}),
            ('low_band',    [-4.35, 1.0, -2.35], [0.35, 2.0, 2.35], 'joint', {}),
            ('front_outer', [-3.3, 2.0, -2.4], [-2.8, 10.4, -2.0], 'brass', {}),
            ('front_inner', [-1.2, 2.0, -2.4], [-0.7, 10.4, -2.0], 'brass', {}),
            ('back_outer',  [-3.3, 2.0, 2.0], [-2.8, 10.4, 2.4], 'brass', {}),
            ('back_inner',  [-1.2, 2.0, 2.0], [-0.7, 10.4, 2.4], 'brass', {}),
            ('knee_pulley', [-4.4, 5.2, -0.8], [-4.0, 6.8, 0.8], 'gear', {}),
        ]},
    # Woven synthetic muscle sleeves with magenta seams.
    'nano_weave_muscle': {
        'arm': [
            ('bicep',   [-8.45, 17.6, -2.45], [-3.55, 22.4, 2.45], 'weave', {}),
            ('forearm', [-8.4, 13.6, -2.4], [-3.6, 16.8, 2.4], 'weave', {}),
            ('elbow',   [-8.3, 16.8, -2.3], [-3.7, 17.6, 2.3], 'joint', {}),
            ('seam',    [-8.6, 14.0, -0.3], [-8.4, 22.0, 0.3], 'glow_magenta', {}),
        ],
        'leg': [
            ('thigh', [-4.35, 6.6, -2.35], [0.35, 11.6, 2.35], 'weave', {}),
            ('knee',  [-4.3, 5.6, -2.3], [0.3, 6.6, 2.3], 'joint', {}),
            ('calf',  [-4.35, 1.2, -2.35], [0.35, 5.6, 2.35], 'weave', {}),
            ('seam',  [-4.4, 1.6, -0.3], [-4.35, 11.2, 0.3], 'glow_magenta', {}),
        ]},
    # ---------------------------------------------------------------- legs
    'hyper_legs': {'leg': [
        ('thigh_plate', [-3.6, 7.2, -2.4], [-0.4, 11.4, -1.6], 'brass_plate', {}),
        ('knee_cap',    [-3.3, 5.2, -2.4], [-0.7, 7.2, -1.2], 'brass_plate', {}),
        ('shin_plate',  [-3.6, 1.4, -2.4], [-0.4, 5.2, -1.6], 'brass_plate', {}),
        ('outer_strut', [-4.4, 1.0, -1.0], [-3.6, 11.0, 0.2], 'brass', {}),
        ('cable',       [-4.35, 2.0, 0.5], [-3.6, 10.0, 1.1], 'cable', {}),
    ]},
    # ---------------------------------------------------------------- hands (arm y 12..13.4)
    'cybernetic_hand': {'hand': [
        ('glove',    [-8.45, 12.0, -2.45], [-3.55, 13.4, 2.45], 'panel', {'west': 'plate'}),
        ('sensor',   [-8.7, 12.3, -0.7], [-8.45, 13.1, 0.7], 'glow_cyan', {}),
        ('knuckles', [-8.3, 11.5, -2.3], [-3.7, 12.0, 2.3], 'joint', {}),
    ]},
    'pulse_wrist': {'hand': [
        ('bracer',  [-8.45, 12.2, -2.45], [-3.55, 13.4, 2.45], 'yellow', {}),
        ('emitter', [-8.8, 12.4, -0.8], [-8.45, 13.2, 0.8], 'glow_cyan', {}),
        ('trim',    [-8.4, 13.4, -2.4], [-3.6, 13.8, 2.4], 'joint', {}),
    ]},
    # ---------------------------------------------------------------- eyes (right eye: x -3..-1, y 27..28)
    'hyper_vision_lens': {'eye': [
        ('lens',       [-3.5, 26.5, -4.75], [-0.5, 29.3, -4.05], 'plate', {'north': 'hyper_lens'}),
        ('lens_arm',   [-4.8, 27.6, -4.6], [-3.5, 28.2, -4.2], 'cable', {}),
        ('temple_run', [-4.8, 27.6, -4.2], [-4.55, 28.2, -1.4], 'cable', {}),
    ]},
    'bio_optic_eye': {'eye': [
        ('optic', [-3.5, 26.5, -4.75], [-0.5, 29.3, -4.05], 'steel', {'north': 'optic_lens'}),
        ('brow',  [-3.5, 29.3, -4.6], [-0.5, 29.7, -4.05], 'panel', {}),
        ('stalk', [-4.4, 27.4, -4.5], [-3.5, 28.0, -4.05], 'cable', {}),
    ]},
    # Navy lens housing with a red digital pupil and a brass mount.
    'digital_retina': {'eye': [
        ('housing', [-3.5, 26.5, -4.75], [-0.5, 29.3, -4.05], 'plate', {'north': 'retina_lens'}),
        ('mount',   [-4.8, 27.6, -4.5], [-3.5, 28.2, -4.1], 'brass', {}),
        ('screw',   [-4.9, 27.2, -4.4], [-4.55, 28.6, -3.4], 'brass_plate', {}),
    ]},
    # Big white-and-blue eyeball lens in a navy barrel.
    'optic_enhancer': {'eye': [
        ('barrel', [-3.8, 26.2, -4.6], [-0.2, 29.6, -4.05], 'plate', {}),
        ('lens',   [-3.4, 26.6, -4.85], [-0.6, 29.2, -4.6], 'steel', {'north': 'eyeball_lens'}),
    ]},
    # ---------------------------------------------------------------- head (box ±4, y 24..32, z ±4)
    'neural_interface': {'head': [
        ('band_right',  [-4.85, 27.0, -1.5], [-4.55, 28.4, 4.85], 'brass', {}),
        ('band_left',   [4.55, 27.0, -1.5], [4.85, 28.4, 4.85], 'brass', {}),
        ('band_back',   [-4.85, 27.0, 4.55], [4.85, 28.4, 4.85], 'brass', {}),
        ('temple_node', [-4.9, 26.2, -2.9], [-4.55, 29.2, -0.9], 'plate', {'west': 'glow_magenta'}),
        ('temple_wire', [-4.88, 25.4, -2.2], [-4.6, 26.2, -1.6], 'cable', {}),
        ('neck_plug',   [-1.2, 24.2, 4.55], [1.2, 27.0, 4.85], 'joint', {}),
        ('plug_light',  [-0.5, 25.0, 4.85], [0.5, 26.2, 4.9], 'glow_magenta', {}),
    ]},
    # Chip socketed in the back of the skull with a red status light.
    'augment_brain_chip': {'head': [
        ('chip',  [-3.4, 28.0, 4.55], [-0.4, 31.2, 4.85], 'plate', {}),
        ('light', [-2.4, 29.2, 4.85], [-1.4, 30.2, 4.9], 'glow_red', {}),
        ('pins',  [-3.2, 27.4, 4.55], [-0.6, 28.0, 4.8], 'joint', {}),
        ('lead',  [-0.4, 29.4, 4.55], [2.4, 30.0, 4.8], 'cable', {}),
    ]},
    # Exposed cortex dome on the crown with a cyan spine and rear stem.
    'cyber_brain': {'head': [
        ('dome',  [-3.6, 32.0, -3.0], [3.6, 32.8, 3.6], 'brain', {}),
        ('spine', [-0.5, 32.4, -3.2], [0.5, 32.9, 3.8], 'glow_cyan', {}),
        ('stem',  [-1.0, 28.6, 4.55], [1.0, 32.0, 4.85], 'joint', {}),
        ('rim',   [-3.9, 31.6, 3.6], [3.9, 32.4, 4.8], 'plate', {}),
    ]},
    # Side module with a blue screen, cabled to the back of the neck.
    'data_hub': {'head': [
        ('module', [-4.9, 26.8, -1.6], [-4.55, 30.6, 2.0], 'plate', {'west': 'screen'}),
        ('cable',  [-4.85, 25.6, 1.2], [-4.6, 26.8, 1.8], 'cable', {}),
        ('run',    [-4.85, 25.0, 1.2], [-4.6, 25.6, 4.7], 'cable', {}),
        ('port',   [-4.6, 24.4, 4.55], [-2.6, 25.8, 4.85], 'plate', {'south': 'glow_cyan'}),
    ]},
    # Ear cups with red LED strips joined by a brass band over the crown.
    'giga_ear': {'head': [
        ('cup_right',  [-4.9, 25.8, -0.6], [-4.55, 29.6, 2.6], 'plate', {'west': 'ear_grille'}),
        ('cup_left',   [4.55, 25.8, -0.6], [4.9, 29.6, 2.6], 'plate', {'east': 'ear_grille'}),
        ('band_right', [-4.85, 29.6, 0.4], [-4.55, 32.85, 1.4], 'brass', {}),
        ('band_left',  [4.55, 29.6, 0.4], [4.85, 32.85, 1.4], 'brass', {}),
        ('band_top',   [-4.85, 32.55, 0.4], [4.85, 32.85, 1.4], 'brass', {}),
    ]},
    # Steel jaw plate over the chin with brass hinges at the cheeks.
    'mech_jaw': {'head': [
        ('jaw',         [-3.6, 24.0, -4.7], [3.6, 25.6, -4.05], 'steel', {'north': 'jaw_grille'}),
        ('hinge_right', [-4.85, 24.2, -3.2], [-4.55, 26.4, -1.0], 'gear', {}),
        ('hinge_left',  [4.55, 24.2, -3.2], [4.85, 26.4, -1.0], 'gear', {}),
        ('strut_right', [-4.8, 24.2, -4.6], [-3.6, 24.8, -4.05], 'brass', {}),
        ('strut_left',  [3.6, 24.2, -4.6], [4.8, 24.8, -4.05], 'brass', {}),
        ('under',       [-3.6, 23.6, -4.6], [3.6, 24.0, -1.0], 'plate', {}),
    ]},
    # Green receiver chip behind the ear, looped by magenta cable to a temple lens.
    'mind_link': {'head': [
        ('receiver',  [-4.9, 28.0, 1.0], [-4.55, 30.2, 3.4], 'pcb', {}),
        ('loop_back', [-4.85, 26.4, 3.0], [-4.6, 28.0, 3.6], 'cable', {}),
        ('loop_low',  [-4.85, 26.0, -1.8], [-4.6, 26.6, 3.6], 'cable', {}),
        ('temple',    [-4.9, 26.0, -3.2], [-4.55, 27.8, -1.8], 'steel', {'west': 'glow_magenta'}),
    ]},
    # Cyan crystal mounted upright on the back of the head on clamp legs.
    'nano_processor': {'head': [
        ('crystal',   [-0.9, 28.2, 4.6], [0.9, 31.6, 4.9], 'crystal', {}),
        ('cap',       [-1.2, 31.6, 4.55], [1.2, 32.2, 4.85], 'steel', {}),
        ('base',      [-1.6, 27.4, 4.55], [1.6, 28.2, 4.85], 'plate', {}),
        ('leg_right', [-2.4, 26.2, 4.55], [-1.6, 28.0, 4.8], 'joint', {}),
        ('leg_left',  [1.6, 26.2, 4.55], [2.4, 28.0, 4.8], 'joint', {}),
    ]},
    # Brass horn on the temple with a dark lens, cabled back past the ear.
    'quantum_interface': {'head': [
        ('horn',  [-4.9, 27.0, -3.6], [-4.55, 30.0, -0.6], 'brass_plate', {'west': 'dark_lens'}),
        ('coil',  [-4.85, 30.0, -3.0], [-4.55, 30.8, -1.2], 'gear', {}),
        ('cable', [-4.85, 27.4, -0.6], [-4.6, 28.0, 4.2], 'cable', {}),
        ('jack',  [-4.8, 26.8, 4.2], [-3.2, 28.6, 4.8], 'plate', {}),
    ]},
    # Grey sensor spindle across the back of the head with an antenna nub.
    'sensory_module': {'head': [
        ('spindle',    [-3.4, 27.2, 4.55], [3.4, 29.0, 4.85], 'chrome', {}),
        ('band_right', [-2.2, 27.0, 4.55], [-1.6, 29.2, 4.9], 'cable', {}),
        ('band_left',  [1.6, 27.0, 4.55], [2.2, 29.2, 4.9], 'cable', {}),
        ('cap_right',  [-4.0, 27.6, 4.5], [-3.4, 28.6, 4.8], 'steel', {}),
        ('cap_left',   [3.4, 27.6, 4.5], [4.0, 28.6, 4.8], 'steel', {}),
        ('antenna',    [-0.4, 32.0, 2.4], [0.4, 32.8, 3.6], 'steel', {'up': 'glow_magenta'}),
    ]},
    # Earpiece with a boom arm holding a small cyan HUD above the right eye.
    'virtual_assistant_node': {'head': [
        ('earpiece', [-4.9, 26.4, 0.0], [-4.55, 29.0, 2.6], 'plate', {'west': 'glow_cyan'}),
        ('boom',     [-4.85, 29.4, -3.6], [-4.6, 30.0, 0.0], 'cable', {}),
        ('riser',    [-4.85, 29.0, -0.6], [-4.6, 29.4, 0.0], 'cable', {}),
        ('hud',      [-4.6, 29.4, -4.75], [-2.4, 30.8, -4.1], 'steel', {'north': 'hud'}),
    ]},
    # ---------------------------------------------------------------- torso (box ±4, y 12..24, z ±2)
    'power_core': {'torso': [
        ('housing',     [-2.6, 16.5, -2.8], [2.6, 21.5, -1.9], 'plate', {}),
        ('indicator',   [-1.1, 17.0, -2.9], [1.1, 21.0, -2.8], 'leds', {}),
        ('vent_right',  [-2.5, 21.5, -2.6], [-1.5, 23.6, -1.6], 'chrome', {}),
        ('vent_left',   [1.5, 21.5, -2.6], [2.5, 23.6, -1.6], 'chrome', {}),
        ('cap_right',   [-2.7, 23.6, -2.8], [-1.3, 24.2, -1.4], 'steel', {}),
        ('cap_left',    [1.3, 23.6, -2.8], [2.7, 24.2, -1.4], 'steel', {}),
        ('heat_sink',   [-2.4, 16.0, 1.9], [2.4, 21.6, 2.7], 'vent', {}),
        ('strap_right', [-3.6, 18.4, -2.35], [-2.6, 19.4, 2.35], 'joint', {}),
        ('strap_left',  [2.6, 18.4, -2.35], [3.6, 19.4, 2.35], 'joint', {}),
    ]},
    # Green circuit board strapped over the chest with navy chips.
    'augment_shield': {'torso': [
        ('board',     [-3.0, 16.0, -2.7], [3.0, 22.4, -2.05], 'pcb', {}),
        ('chip_a',    [-2.2, 19.2, -2.9], [-0.6, 20.8, -2.7], 'plate', {}),
        ('chip_b',    [0.6, 17.0, -2.9], [2.2, 18.6, -2.7], 'plate', {}),
        ('connector', [1.6, 22.4, -2.6], [2.8, 23.4, -2.1], 'brass_plate', {}),
        ('lead',      [-2.8, 22.4, -2.5], [-2.2, 23.8, -2.1], 'cable_green', {}),
    ]},
    # Magenta nerve trunk with branches down the chest and back, glowing at the junction.
    'augmented_nervous_system': {'torso': [
        ('trunk_front', [-0.4, 13.2, -2.5], [0.4, 23.6, -2.1], 'cable', {}),
        ('branch_high', [-3.4, 20.6, -2.45], [3.4, 21.2, -2.15], 'cable', {}),
        ('branch_low',  [-2.8, 17.0, -2.45], [2.8, 17.6, -2.15], 'cable', {}),
        ('junction',    [-0.7, 20.3, -2.7], [0.7, 21.5, -2.4], 'glow_magenta', {}),
        ('trunk_back',  [-0.4, 13.2, 2.1], [0.4, 23.6, 2.5], 'cable', {}),
        ('branch_back', [-3.0, 19.0, 2.15], [3.0, 19.6, 2.45], 'cable', {}),
    ]},
    # Navy sensor unit with twin cyan lenses and a green lead.
    'bio_feedback_system': {'torso': [
        ('unit',    [-3.2, 17.6, -2.7], [-0.2, 21.6, -2.05], 'plate', {}),
        ('lens_hi', [-2.6, 19.8, -2.9], [-1.2, 21.1, -2.7], 'glow_cyan', {}),
        ('lens_lo', [-2.6, 18.1, -2.9], [-1.2, 19.4, -2.7], 'glow_cyan', {}),
        ('lead',    [-0.2, 19.2, -2.5], [3.0, 19.8, -2.1], 'cable_green', {}),
        ('clip',    [3.0, 18.6, -2.6], [3.6, 20.4, -2.05], 'brass_plate', {}),
    ]},
    # Reinforced ribcage over the chest with a sternum plate and back spine.
    'bio_skeleton': {'torso': [
        ('sternum',   [-0.7, 15.4, -2.8], [0.7, 23.0, -2.3], 'plate', {}),
        ('rib_1',     [-3.6, 21.4, -2.5], [3.6, 22.0, -2.1], 'steel', {}),
        ('rib_2',     [-3.4, 19.8, -2.5], [3.4, 20.4, -2.1], 'steel', {}),
        ('rib_3',     [-3.2, 18.2, -2.5], [3.2, 18.8, -2.1], 'steel', {}),
        ('rib_4',     [-2.8, 16.6, -2.5], [2.8, 17.2, -2.1], 'steel', {}),
        ('spine',     [-0.7, 12.6, 2.1], [0.7, 23.8, 2.6], 'joint', {}),
        ('back_ribs', [-3.4, 19.0, 2.1], [3.4, 19.6, 2.5], 'steel', {}),
    ]},
    # Segmented vertebrae down the back over a glowing core.
    'cybernetic_spine': {'torso': [
        ('core',  [-0.4, 12.6, 2.2], [0.4, 23.8, 2.5], 'glow_magenta', {}),
        ('seg_1', [-1.1, 22.4, 2.1], [1.1, 23.6, 2.85], 'plate', {}),
        ('seg_2', [-1.1, 20.6, 2.1], [1.1, 21.8, 2.85], 'plate', {}),
        ('seg_3', [-1.1, 18.8, 2.1], [1.1, 20.0, 2.85], 'plate', {}),
        ('seg_4', [-1.1, 17.0, 2.1], [1.1, 18.2, 2.85], 'plate', {}),
        ('seg_5', [-1.1, 15.2, 2.1], [1.1, 16.4, 2.85], 'plate', {}),
        ('seg_6', [-1.1, 13.4, 2.1], [1.1, 14.6, 2.85], 'plate', {}),
        ('neck',  [-1.4, 23.6, 1.6], [1.4, 24.6, 2.8], 'joint', {}),
    ]},
    # Converter box at the hip with a magenta window, piped across to a brass fitting.
    'energy_converter': {'torso': [
        ('unit',    [-3.6, 15.4, -2.8], [-0.8, 18.4, -2.05], 'plate', {'north': 'converter'}),
        ('pipe',    [-0.8, 16.4, -2.5], [2.8, 17.0, -2.1], 'cable', {}),
        ('fitting', [2.8, 15.8, -2.7], [3.6, 17.6, -2.05], 'brass_plate', {}),
        ('feed',    [-2.6, 18.4, -2.5], [-2.0, 21.0, -2.1], 'brass', {}),
    ]},
    # Twin thrusters on the back with cyan exhaust glow.
    'impulse_booster': {'torso': [
        ('thruster_right', [-3.0, 16.0, 2.1], [-1.0, 22.4, 2.9], 'chrome', {}),
        ('thruster_left',  [1.0, 16.0, 2.1], [3.0, 22.4, 2.9], 'chrome', {}),
        ('nozzle_right',   [-2.8, 15.2, 2.2], [-1.2, 16.0, 2.8], 'glow_cyan', {}),
        ('nozzle_left',    [1.2, 15.2, 2.2], [2.8, 16.0, 2.8], 'glow_cyan', {}),
        ('bridge',         [-1.0, 19.0, 2.1], [1.0, 21.0, 2.6], 'plate', {}),
        ('harness',        [-3.6, 21.6, -2.35], [3.6, 22.4, 2.35], 'joint', {}),
    ]},
    # Vial of pink nanite fluid on the chest feeding a line across.
    'nano_bloodstream': {'torso': [
        ('vial',    [0.8, 16.2, -2.9], [2.8, 21.4, -2.05], 'hydraulic', {}),
        ('cap_top', [0.6, 21.4, -2.8], [3.0, 22.2, -2.05], 'steel', {}),
        ('cap_low', [0.6, 15.4, -2.8], [3.0, 16.2, -2.05], 'steel', {}),
        ('line',    [-2.2, 18.4, -2.5], [0.8, 19.0, -2.1], 'cable', {}),
        ('port',    [-2.8, 17.8, -2.7], [-2.2, 19.6, -2.05], 'plate', {}),
    ]},
    # Thin circuit-mesh plating over the chest and back with a glowing seam.
    'nano_skin': {'torso': [
        ('front',  [-3.6, 13.6, -2.4], [3.6, 23.6, -2.3], 'nano_mesh', {}),
        ('back',   [-3.6, 13.6, 2.3], [3.6, 23.6, 2.4], 'nano_mesh', {}),
        ('seam',   [-0.4, 13.6, -2.5], [0.4, 23.6, -2.4], 'glow_blue', {}),
        ('collar', [-3.8, 23.6, -2.4], [3.8, 24.2, 2.4], 'joint', {}),
    ]},
    # Grey pulse capsule with a blue light on the chest, strapped under the arm.
    'pulse_enhancer': {'torso': [
        ('capsule', [-3.2, 16.2, -2.9], [-1.4, 22.4, -2.05], 'chrome', {'north': 'capsule'}),
        ('tip',     [-2.6, 22.4, -2.7], [-2.0, 23.6, -2.3], 'brass', {}),
        ('base',    [-3.0, 15.6, -2.7], [-1.6, 16.2, -2.2], 'steel', {}),
        ('strap',   [-4.0, 18.8, -2.4], [-1.4, 19.6, 2.4], 'joint', {}),
    ]},
    # Synthetic heart on the left of the chest with vessel lines.
    'synthetic_heart': {'torso': [
        ('heart', [0.4, 17.4, -2.9], [3.4, 21.2, -2.05], 'plate', {'north': 'heart'}),
        ('aorta', [1.0, 21.2, -2.6], [1.7, 23.4, -2.2], 'cable', {}),
        ('vein',  [2.2, 21.2, -2.6], [2.8, 22.8, -2.2], 'cable_blue', {}),
        ('pacer', [-1.6, 18.2, -2.7], [0.4, 19.8, -2.05], 'plate', {'north': 'glow_red'}),
    ]},
    # Two brass lungs on the chest joined by a chrome trachea.
    'synthetic_lungs': {'torso': [
        ('lung_right', [-3.5, 16.2, -2.7], [-0.7, 22.0, -2.05], 'brass_plate', {'north': 'lung'}),
        ('lung_left',  [0.7, 16.2, -2.7], [3.5, 22.0, -2.05], 'brass_plate', {'north': 'lung'}),
        ('trachea',    [-0.5, 19.6, -2.6], [0.5, 23.8, -2.2], 'chrome', {}),
        ('valve',      [-0.8, 18.8, -2.8], [0.8, 19.6, -2.2], 'glow_magenta', {}),
    ]},
    # Finned navy core in the middle of the chest.
    'techno_core': {'torso': [
        ('core', [-2.2, 16.6, -2.9], [2.2, 21.4, -2.05], 'fins', {}),
        ('cap',  [-2.6, 21.4, -2.8], [2.6, 22.2, -2.05], 'plate', {}),
        ('base', [-2.6, 15.8, -2.8], [2.6, 16.6, -2.05], 'plate', {}),
        ('bolt', [-0.6, 22.2, -2.6], [0.6, 23.4, -2.2], 'brass', {}),
    ]},
}

FACE_DIMS = {'north': (0, 1), 'south': (0, 1), 'east': (2, 1), 'west': (2, 1), 'up': (0, 2), 'down': (0, 2)}
MIRROR_BONE = {'right_arm': 'left_arm', 'left_arm': 'right_arm', 'right_leg': 'left_leg', 'left_leg': 'right_leg'}


def texels(v): return max(1, round(v))


def catalog():
    return {d['id']: d['slots'] for d in json.loads((ASSETS / 'implants.json').read_text()) if d['slots']}


def check_fit(name, bone, cubes):
    """Fail if any cube would poke through armour worn on that limb."""
    if name.split(' ')[0] in EXPOSED: return
    lo, hi, reach = LIMBS[bone]
    bad = [c['name'] for c in cubes
           if any(c['from'][k] < lo[k] - reach - 1e-6 or c['to'][k] > hi[k] + reach + 1e-6 for k in range(3))]
    if bad: raise SystemExit(f'{name}: {", ".join(bad)} reach past the armour shell (max {reach} beyond the {bone})')


def paint(name):
    """Paint the colour atlas and glow mask; return cubes with per-face UVs for each design kind."""
    kinds = IMPLANTS[name]
    patches = []  # (kind, part index, face, w, h, material)
    for kind, parts in kinds.items():
        for i, (_, a, b, mat, over) in enumerate(parts):
            size = [b[k] - a[k] for k in range(3)]
            for face, (u, v) in FACE_DIMS.items():
                patches.append((kind, i, face, texels(size[u]), texels(size[v]), over.get(face, mat)))
    # Shelf-pack tallest first into the smallest power-of-two square that fits.
    order = sorted(range(len(patches)), key=lambda k: (-patches[k][4], -patches[k][3]))
    side = 16
    while True:
        x = y = row = 0; spots = {}; ok = True
        for k in order:
            w, h = patches[k][3], patches[k][4]
            if x + w > side: x, y, row = 0, y + row, 0
            if y + h > side: ok = False; break
            spots[k] = (x, y); x += w; row = max(row, h)
        if ok: break
        side *= 2
    img = Image.new('RGBA', (side, side), CLEAR); px = img.load()
    glow = Image.new('RGBA', (side, side), CLEAR); gx = glow.load()
    cubes = {kind: [{'name': p[0], 'from': p[1], 'to': p[2], 'faces': {}} for p in parts] for kind, parts in kinds.items()}
    for k, (kind, i, face, w, h, mat) in enumerate(patches):
        ox, oy = spots[k]; brush = MATERIALS[mat]
        for yy in range(h):
            for xx in range(w):
                c = brush(xx, yy, w, h); px[ox + xx, oy + yy] = c
                if mat in GLOWING and c in GLOW_COLOURS: gx[ox + xx, oy + yy] = c
        cubes[kind][i]['faces'][face] = [ox, oy, ox + w, oy + h]
    TEXTURES.mkdir(parents=True, exist_ok=True)
    img.save(TEXTURES / f'{name}.png')
    has_glow = glow.getbbox() is not None
    if has_glow: glow.save(TEXTURES / f'{name}_glow.png')
    else: (TEXTURES / f'{name}_glow.png').unlink(missing_ok=True)
    return side, has_glow, cubes


def side_of(kind, cubes):
    if kind == 'eye': return 'right' if sum(c['from'][0] + c['to'][0] for c in cubes) < 0 else 'left'
    return {'hand': 'right', 'arm': 'right', 'leg': 'right'}.get(kind)


def mirrored(cubes):
    """Reflect across x=0: swap east/west faces and flip every UV so the art reads the same way."""
    out = []
    for c in cubes:
        faces = {}
        for f, (u0, v0, u1, v1) in c['faces'].items():
            faces[{'east': 'west', 'west': 'east'}.get(f, f)] = [u1, v0, u0, v1]
        out.append({'name': c['name'], 'from': [-c['to'][0], c['from'][1], c['from'][2]],
                    'to': [-c['from'][0], c['to'][1], c['to'][2]], 'faces': faces})
    return out


def variant(cubes, kind, want):
    """The design for one slot: its bone and cubes, mirrored when the slot is on the other side."""
    bone = KIND_BONE[kind]
    if want == 'left': bone = MIRROR_BONE.get(bone, bone)
    if want is None or side_of(kind, cubes) == want: return bone, cubes
    return bone, mirrored(cubes)


def quads(cubes, bone, size):
    """Faces as quads in ModelPart space: relative to the bone pivot, in blocks, y pointing down."""
    px, py, pz = BONES[bone]
    out = []
    for c in cubes:
        x0, y0, z0 = c['from']; x1, y1, z1 = c['to']
        corners = {
            'north': ([[x1, y0, z0], [x0, y0, z0], [x0, y1, z0], [x1, y1, z0]], [0, 0, -1]),
            'south': ([[x0, y0, z1], [x1, y0, z1], [x1, y1, z1], [x0, y1, z1]], [0, 0, 1]),
            'west':  ([[x0, y0, z0], [x0, y0, z1], [x0, y1, z1], [x0, y1, z0]], [-1, 0, 0]),
            'east':  ([[x1, y0, z1], [x1, y0, z0], [x1, y1, z0], [x1, y1, z1]], [1, 0, 0]),
            'up':    ([[x0, y1, z1], [x1, y1, z1], [x1, y1, z0], [x0, y1, z0]], [0, 1, 0]),
            'down':  ([[x0, y0, z0], [x1, y0, z0], [x1, y0, z1], [x0, y0, z1]], [0, -1, 0]),
        }
        for face, uv in c['faces'].items():
            verts, n = corners[face]
            out.append({'v': [[round((x - px) / 16, 5), round((py - y) / 16, 5), round((z - pz) / 16, 5)] for x, y, z in verts],
                        'uv': [round(t / size, 6) for t in uv], 'n': [n[0], -n[1], n[2]]})
    return out


def build(name, slots, export=True):
    """Paint, fit-check and export one implant. Returns its Blockbench specs, one per slot given."""
    size, has_glow, designs = paint(name)
    model = {'size': size, 'glow': has_glow, 'slots': {}}
    specs = []
    for slot in slots:
        kind, want = SLOTS[slot]
        if kind == 'hand' and kind not in designs: kind = 'arm'
        if kind not in designs: raise SystemExit(f'{name}: no {kind} design for slot {slot}')
        bone, cubes = variant(designs[kind], kind, want)
        check_fit(f'{name} ({slot})', bone, cubes)
        model['slots'][slot] = {'part': bone, 'quads': quads(cubes, bone, size)}
        specs.append({'name': name, 'slot': slot, 'bone': bone, 'pivot': BONES[bone], 'size': size, 'cubes': cubes})
    if export:
        MODELS.mkdir(parents=True, exist_ok=True)
        (MODELS / f'{name}.json').write_text(json.dumps(model, separators=(',', ':')) + '\n')
        print(f'{name}: {", ".join(slots)}; {size}x{size} atlas{" + glow" if has_glow else ""}')
    return specs


def write(script, specs):
    BUILD.mkdir(exist_ok=True)
    js = (HERE / 'blockbench_build.js').read_text().replace('__SPEC__', json.dumps(specs))
    (BUILD / f'{script}.js').write_text(js)


if __name__ == '__main__':
    args = sys.argv[1:]
    worn = catalog()
    missing = sorted(set(worn) - set(IMPLANTS))
    if missing: raise SystemExit('no design yet for: ' + ', '.join(missing))
    if args[:1] == ['--scene']:
        # Each argument is an implant, or implant:slot; a bare implant takes its first catalog slot.
        specs = []
        for a in args[1:]:
            n, _, slot = a.partition(':')
            specs += build(n, [slot or worn[n][0]], export=False)
        write('scene', specs)
    else:
        for n in args or IMPLANTS: write(n, build(n, worn[n]))
