"""Sync web color tokens and offline Latin font subsets from Android (fonttools)."""
from pathlib import Path
import base64, io, re
from fontTools import subset
from fontTools.ttLib import TTFont
ROOT = Path(__file__).resolve().parents[2]
android = ROOT / 'companion/android/app/src/main'
p = ROOT / 'headphone-stand/firmware/src/editor.html'
s = p.read_text()
colors = re.findall(r'val (\w+) = Color\(0xFF([0-9A-F]{6})\)', (android / 'java/io/github/cespresso/clumo/design/ClumoColors.kt').read_text())
css = ':root{' + ''.join('--'+re.sub(r'(?<!^)(?=[A-Z])','-',name).lower()+':#'+value+';' for name,value in colors) + '}\n'
for name,weight in [('regular',400),('bold',700)]:
 font = TTFont(android / f'res/font/mplus_rounded1c_{name}.ttf')
 options = subset.Options(); options.flavor = 'woff'
 sub = subset.Subsetter(options=options);sub.populate(unicodes=list(range(32,127))+[0x2190,0x2192,0x2026,0x00d7,0x00b7,0x2019]);sub.subset(font)
 font.flavor='woff';out=io.BytesIO();font.save(out)
 css += "@font-face{font-family:ClumoRounded;font-style:normal;font-weight:"+str(weight)+";font-display:swap;src:url(data:font/woff;base64,"+base64.b64encode(out.getvalue()).decode()+") format('woff')}\n"
block='/* BEGIN ANDROID TOKENS */\n'+css+'/* END ANDROID TOKENS */'
s = re.sub(r'/\* BEGIN ANDROID TOKENS \*/.*?/\* END ANDROID TOKENS \*/',lambda _:block,s,flags=re.S) if '/* BEGIN ANDROID TOKENS */' in s else s.replace('<style>','<style>\n'+block,1)
p.write_text(s)
print('Synced Android colors and offline fonts')
