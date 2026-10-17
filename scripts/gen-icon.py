#!/usr/bin/env python3
# Kartrid app icon generator - BMO-style rounded Gameboy, design-system riso-volt DNA.
# DNA: flat riso, NO gradient/blur/glass, ink border, hard offset shadow (0 blur).
import os
from PIL import Image, ImageDraw, ImageFont

INK="#16121F"; PAPER_BG="#EAE6F2"; SURFACE="#F7F5FC"; LIME="#C3E82F"
VIOLET="#6D3BE8"; VIOLET_L="#A98BFF"; MUTED="#DDD8EA"; GOLD="#FFC94D"

def hx(h):
    h=h.lstrip('#')
    return tuple(int(h[i:i+2],16) for i in (0,2,4))+(255,)

def font(sz):
    for p in ["/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
              "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"]:
        if os.path.exists(p):
            try: return ImageFont.truetype(p, sz)
            except Exception: pass
    return ImageFont.load_default()

def draw_icon(size, body, face, ctrl, screen=INK, shadow=INK):
    S=size
    img=Image.new("RGBA",(S,S),(0,0,0,0))
    d=ImageDraw.Draw(img)
    bx0,by0,bx1,by1=0.115,0.095,0.855,0.835
    rad=0.17*S
    ox=oy=0.045*S
    # hard offset shadow, 0 blur
    d.rounded_rectangle([bx0*S+ox,by0*S+oy,bx1*S+ox,by1*S+oy],radius=rad,fill=hx(shadow))
    # body
    d.rounded_rectangle([bx0*S,by0*S,bx1*S,by1*S],radius=rad,fill=hx(body),outline=hx(INK),width=int(0.030*S))
    # screen
    sx0,sy0,sx1,sy1=0.20,0.175,0.775,0.525
    d.rounded_rectangle([sx0*S,sy0*S,sx1*S,sy1*S],radius=0.055*S,fill=hx(screen),outline=hx(INK),width=int(0.017*S))
    # face: two tall eyes + smile
    ex=[0.365,0.61]; ey=0.305; erx=0.042; ery=0.058
    for cx in ex:
        d.ellipse([(cx-erx)*S,(ey-ery)*S,(cx+erx)*S,(ey+ery)*S],fill=hx(face))
    d.arc([0.425*S,0.30*S,0.55*S,0.44*S],start=15,end=165,fill=hx(face),width=int(0.022*S))
    # d-pad
    dcx,dcy=0.30,0.685; arm=0.072; aw=0.045
    d.rounded_rectangle([(dcx-arm)*S,(dcy-aw)*S,(dcx+arm)*S,(dcy+aw)*S],radius=0.018*S,fill=hx(ctrl))
    d.rounded_rectangle([(dcx-aw)*S,(dcy-arm)*S,(dcx+aw)*S,(dcy+arm)*S],radius=0.018*S,fill=hx(ctrl))
    # buttons A/B
    for (cx,cy,r) in [(0.615,0.705,0.046),(0.705,0.615,0.046)]:
        d.ellipse([(cx-r)*S,(cy-r)*S,(cx+r)*S,(cy+r)*S],fill=hx(ctrl),outline=hx(INK),width=int(0.012*S))
    return img

def squircle_mask(size, rad):
    m=Image.new("L",(size,size),0); ImageDraw.Draw(m).rounded_rectangle([0,0,size-1,size-1],radius=rad,fill=255); return m

def circle_mask(size):
    m=Image.new("L",(size,size),0); ImageDraw.Draw(m).ellipse([0,0,size-1,size-1],fill=255); return m

BASE="/home/iqbaladinur/hobbies/kartrid/previews"
VAR={"A":dict(body=LIME,face=SURFACE,ctrl=VIOLET),
     "B":dict(body=SURFACE,face=LIME,ctrl=VIOLET),
     "C":dict(body=VIOLET,face=LIME,ctrl=LIME)}

W,H=1680,1040
sheet=Image.new("RGB",(W,H),hx(INK)[:3])
d=ImageDraw.Draw(sheet)
d.text((60,40),"Kartrid app icon - riso-volt DNA",font=font(46),fill=hx(SURFACE)[:3])
d.text((60,100),"BMO-style rounded Gameboy: ink border, hard offset shadow (0 blur), flat riso fills",font=font(24),fill=hx(MUTED)[:3])

big=430
xs=[70,620,1170]
for i,(k,cfg) in enumerate(VAR.items()):
    ic=draw_icon(1024,**cfg)
    ic.resize((big,big),Image.LANCZOS).save(f"{BASE}/icon-{k}.png")
    ic.resize((big,big),Image.LANCZOS)
    sheet.paste(ic.resize((big,big),Image.LANCZOS),(xs[i],170),ic.resize((big,big),Image.LANCZOS))
    d.text((xs[i],170+big+18),f"Variant {k}",font=font(34),fill=hx(SURFACE)[:3])
    sub={"A":"lime body / violet ctrl","B":"paper body / lime face","C":"violet body / lime ctrl"}[k]
    d.text((xs[i],170+big+60),sub,font=font(22),fill=hx(MUTED)[:3])

# small sizes + android masks of variant A
yy=170
sizes=[192,144,96,72,48]
xx=70
for s in sizes:
    ic=draw_icon(s,**VAR["A"])
    sheet.paste(ic,(xx,175+big+120),ic)
    d.text((xx,175+big+120+s+8),str(s),font=font(20),fill=hx(MUTED)[:3])
    xx+=s+40

# masked previews (bg layer fills)
mx=700; my=175+big+110
for mask,name,bgc in [(circle_mask(220),"circle",LIME),(squircle_mask(220,66),"squircle",LIME),(squircle_mask(220,66),"squircle",VIOLET)]:
    bg=Image.new("RGBA",(220,220),hx(bgc))
    fg=draw_icon(320,**VAR["A"]).resize((300,300),Image.LANCZOS)
    lay=Image.new("RGBA",(220,220),(0,0,0,0))
    lay.paste(fg,(-40,-40),fg)
    out=Image.composite(lay,bg,mask)
    out.save(f"{BASE}/icon-mask-{name}-{bgc[1:]}.png")
    sheet.paste(out,(mx,my),mask)
    d.text((mx,my+228),name,font=font(20),fill=hx(MUTED)[:3])
    mx+=250

sheet.save(f"{BASE}/20-icon-bmo.png")
print("OK", os.path.getsize(f"{BASE}/20-icon-bmo.png"), "bytes")
print("files:", sorted(f for f in os.listdir(BASE) if f.startswith('icon-')))
