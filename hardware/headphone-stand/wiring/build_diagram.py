"""Physical XIAO pin layout and explicit common-ground wiring."""
from pathlib import Path
from html import escape
P=Path(__file__).resolve().parent
import parts
S=['<svg xmlns="http://www.w3.org/2000/svg" width="1600" height="1600" viewBox="0 0 1600 1600">','<rect width="1600" height="1600" fill="white"/>'];routes=[]
ink='#293237';blue='#3478bc';green='#248e72';yellow='#b99300';red='#d95948';gray='#8b939a'
def text(x,y,t,size=20,c=ink,anchor='start'):
 S.append(f'<text x="{x}" y="{y}" fill="{c}" font-family="Arial,sans-serif" font-size="{size}" text-anchor="{anchor}">{escape(t)}</text>')
def box(x,y,w,h,c='#f0f3f1',r=12):S.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="{c}" stroke="#b9c4be"/>')
def wire(ps,c=ink):
 routes.append(ps);pts=' '.join(f'{x},{y}' for x,y in ps)
 S.append(f'<polyline points="{pts}" fill="none" stroke="white" stroke-width="10" stroke-linejoin="round"/><polyline points="{pts}" fill="none" stroke="{c}" stroke-width="4" stroke-linejoin="round"/>')
def dot(x,y,c=ink):S.append(f'<circle cx="{x}" cy="{y}" r="5" fill="{c}"/>')
def art(part,x,y,k):
 S.append(f'<g transform="translate({x},{y}) scale({k})">{part.body}</g>');return {n:(x+a*k,y+b*k) for n,(a,b) in part.pins.items()}
def resistor(x,y,label):box(x,y-12,90,24,'#efe2bc',2);text(x+45,y+5,label,15,anchor='middle')
text(55,65,'CLumo Stand · Wiring',34)
text(55,105,'XIAO ESP32-C3 · USB ↑',20,gray)
# Actual board artwork: USB up, left D0-D6, right 5V/GND/3V3/D10/D9/D8/D7.
p=art(parts.xiao_esp32c3(),650,350,18)
text(807,770,'TOP VIEW',17,gray,'middle')
m=art(parts.max7219_matrix(),1220,120,6)
text(1215,92,'MAX7219 · IN',22)
# Ground trunk: every ground terminal visibly terminates on this net.
bus=1510; bottom=1350
wire([p['GND'],(bus,p['GND'][1]),(bus,bottom),(90,bottom),(90,420)])
text(1390,1390,'GND',22)
# Matrix routes: power and signals leave the physical pads.
wire([p['5V'],(1080,p['5V'][1]),(1080,375),(m['VCC'][0],375),m['VCC']],red)
wire([m['GND'],(m['GND'][0],395),(1460,395),(1460,p['GND'][1])]);dot(1460,p['GND'][1])
wire([p['D0'],(580,p['D0'][1]),(580,240),(1150,240),(1150,420),(m['DIN'][0],420),m['DIN']],blue)
wire([p['D9'],(1120,p['D9'][1]),(1120,440),(m['CS'][0],440),m['CS']],green)
wire([p['D8'],(1160,p['D8'][1]),(1160,465),(m['CLK'][0],465),m['CLK']],yellow)
# Buttons: real switch artwork, two contacts.
for title,pin,y,c in [('PREV','D1',435,yellow),('NEXT','D2',575,green)]:
 text(190,y-25,title,22);b=art(parts.tact_switch('#e6c7a1'),205,y,10)
 wire([p[pin],(505,p[pin][1]),(505,b['A'][1]),b['A']],c)
 wire([b['B'],(90,b['B'][1])]);dot(90,b['B'][1])
# Encoder underside: A C B across top, push contacts underneath.
text(230,830,'ENCODER · BOTTOM VIEW',22)
box(220,900,230,175,'#dce1df')
e={'A':(245,900),'C':(335,900),'B':(425,900),'SW':(265,1075),'SW_GND':(405,1075)}
for n,(x,y) in e.items():dot(x,y)
wire([p['D4'],(550,p['D4'][1]),(550,850),(245,850),e['A']],yellow)
wire([p['D5'],(525,p['D5'][1]),(525,875),(425,875),e['B']],green)
wire([p['D3'],(595,p['D3'][1]),(595,1150),(265,1150),e['SW']],blue)
wire([e['C'],(335,870),(180,870),(180,bottom)]);dot(180,bottom)
wire([e['SW_GND'],(405,1200),(90,1200)]);dot(90,1200)
# Four LED branches, each independently returns to ground trunk.
text(1180,745,'VOLUME',22);text(1180,775,'220Ω–1kΩ / LED',17,gray)
for i,(pin,c,lab) in enumerate([('D10',yellow,'3'),('D7',green,'2'),('D6',blue,'1'),('3V3',gray,'PWR')]):
 y=830+i*110;x=1030+i*35
 if pin=='D6':wire([p[pin],(610,p[pin][1]),(610,1050),(1130,1050),(1130,y),(1180,y)],c)
 else:wire([p[pin],(x,p[pin][1]),(x,y),(1180,y)],c)
 resistor(1180,y,'220–1kΩ');wire([(1270,y),(1310,y)],c)
 S.append(f'<circle cx="1330" cy="{y}" r="20" fill="{ "#edba91" if i==3 else "#eee7c9"}" stroke="{ink}" stroke-width="2"/>')
 text(1330,y+5,lab,12,anchor='middle');text(1308,y-30,'+',18);text(1340,y-30,'−',18)
 wire([(1350,y),(bus,y)]);dot(bus,y)
# Pull-up branches wired to existing signal paths and XIAO 3V3.
text(690,1240,'PULL-UP · 10kΩ',17,gray)
wire([p['3V3'],(990,p['3V3'][1]),(990,1250),(840,1250)],gray)
wire([(840,1250),(840,1280),(725,1280)],gray);resistor(635,1280,'10kΩ')
wire([(635,1280),(565,1280),(565,240)],blue)
# Branch must land on D0 horizontal path (starts x580), hence x600.

# D0 junction route endpoint corrected to x600 on top horizontal.
wire([(565,240),(600,240)],blue);dot(600,240,blue)
wire([(840,1250),(840,1315),(1170,1315),(1170,p['D8'][1]),(1160,p['D8'][1])],gray)
# resistor drawn inline over branch to D8
resistor(900,1315,'10kΩ');dot(1160,p['D8'][1],yellow)

for n,(x,y) in e.items():
 text(x, y+34 if y==900 else y-23, 'GND' if n=='SW_GND' else n,18,anchor='middle')
dot(*p['3V3'],gray)
text(55,1470,'● Connected   /   Crossing without ● = not connected',18,gray)
text(55,1510,'MAX7219: 5V supply, 3.3V data (tested build; below specified input-high level).',16,gray)
S.append('</svg>');out=P.parent/'img/clumo-headphone-stand-wiring.svg';out.write_text('\n'.join(S));print(out)
