"""Build bilingual controls diagrams and publication previews from local assets.
Imported from maker-projects; see publishing/diagrams/sources.json.
PNG exports are generated separately with qlmanage (see diagrams/README.md).
"""
from pathlib import Path
import base64, html
R = Path(__file__).resolve().parents[2]
O = R / 'headphone-stand/publishing'
D = O / 'diagrams'
img = D / 'control-panel-render.png'
data=base64.b64encode(img.read_bytes()).decode()
copy={
'ja':('操作と表示', '初期割り当て · Android / iPhone',[
('8×8 LEDマトリクス',['操作アニメーションと保存した待機表示。','Androidアプリから音声ビジュアライザも表示。']),
('3連LED：音量',['Android：アプリから実音量を受信。','iPhone：AMS経由、アプリ不要。','実音量が不明なときは、回転時に順次点灯。']),
('電源LED',['下段のオレンジ色の窓は電源表示。']),
('左右ボタン',['左：前へ　／　右：次へ']),
('ノブ',['回す：音量　／　1回押す：再生・一時停止','2回押す：次へ　／　3回押す：前へ'])],
'操作の割り当ては変更できます','Androidアプリ／Wi-Fi設定画面で変更し、初期値へ復元できます。',
'固定の長押し操作',['ノブを3秒：新しい端末のペアリング受付（60秒）','左右ボタンを同時に3秒：Wi-Fi設定へ切替／通常モードへ戻る'],
'Wi-Fi設定中は通常のBluetooth操作を停止します。',
'図は点灯の説明用レンダリングです。全点灯は実際の音量値や表示内容を示しません。'),
'en':('Controls & display','Default assignments · Android / iPhone',[
('8×8 LED matrix',['Control animations and saved standby content.','Audio visualization with the Android app.']),
('Three LEDs: volume',['Android: actual volume from the connected app.','iPhone: via AMS, no app required.','If volume is unknown, LEDs sweep as you turn.']),
('Power indicator',['The lower orange window indicates power.']),
('Left / right buttons',['Left: Previous  /  Right: Next']),
('Rotary knob',['Turn: volume  /  Press once: play or pause','Double press: next  /  Triple press: previous'])],
'Customize the controls','Change assignments in the Android app or Wi-Fi editor; restore defaults anytime.',
'Fixed hold gestures',['Hold knob 3 seconds: pair a new phone (60-second window)','Hold both buttons 3 seconds: enter / leave Wi-Fi setup'],
'Normal Bluetooth operation stops during Wi-Fi setup.',
'Illustrative lit render. All-on LEDs do not represent an actual volume value or display state.')}
for lang,c in copy.items():
 title,sub,labels,custom,desc,fixed,holds,caution,note=c
 svg=['<svg xmlns="http://www.w3.org/2000/svg" width="1800" height="1800" viewBox="0 0 1800 1800">','<rect width="1800" height="1800" fill="#f5f5f2"/>','<style>text{font-family:Arial,"Hiragino Sans",sans-serif;fill:#303435}.title{font-size:56px;font-weight:700}.head{font-size:30px;font-weight:700}.body{font-size:25px}.small{font-size:22px;fill:#636969}</style>']
 def text(x,y,t,cl='body'):svg.append(f'<text x="{x}" y="{y}" class="{cl}">{html.escape(t)}</text>')
 text(75,95,'CLumo  /  '+title,'title');text(78,148,sub,'small')
 svg.append(f'<image x="35" y="240" width="920" height="920" href="data:image/png;base64,{data}"/>')
 for i,(x,y) in enumerate([(260,390),(745,450),(745,695),(460,920),(750,920)],1):
  svg.append(f'<circle cx="{x}" cy="{y}" r="25" fill="#e86830" stroke="#fff" stroke-width="3"/>');svg.append(f'<text x="{x}" y="{y+9}" text-anchor="middle" style="fill:white;font-size:27px;font-weight:bold">{i}</text>')
 for i,(head,lines) in enumerate(labels):
  y=265+i*177
  text(1000,y,f'{i+1:02}  {head}','head')
  for j,line in enumerate(lines):text(1000,y+45+j*36,line)
 svg.append('<rect x="75" y="1220" width="1650" height="150" rx="18" fill="#e8eae7"/>')
 text(105,1270,custom,'head');text(105,1321,desc)
 text(80,1445,fixed,'head')
 for i,line in enumerate(holds):text(80,1500+i*46,line)
 text(80,1610,caution,'small');text(80,1720,note,'small')
 svg.append('</svg>');(D/f'controls.{lang}.svg').write_text('\n'.join(svg))
# Lightweight Markdown rendering for these trusted local tables; no dependency required.
def md(s):
 rows=[];in_table=False
 for line in s.splitlines():
  if line.startswith('|'):
   if set(line.replace('|','').replace(' ','').replace(':',''))=={'-'}:continue
   if not in_table:rows.append('<table>');in_table=True
   rows.append('<tr>'+''.join('<td>'+html.escape(x.strip())+'</td>' for x in line.strip('|').split('|'))+'</tr>');continue
  if in_table:rows.append('</table>');in_table=False
  if line.startswith('#'):level=len(line)-len(line.lstrip('#'));rows.append(f'<h{level}>'+html.escape(line[level:].strip())+f'</h{level}>')
  elif line:rows.append('<p>'+html.escape(line)+'</p>')
 if in_table:rows.append('</table>')
 return '\n'.join(rows)
captions=[('android-connected.png','接続・明るさ・ビジュアライザ','Connection, brightness and visualizer'),('android-display-editor.png','待機表示の編集：静止画／最大8コマ','Standby editor: a still image or up to 8 frames'),('android-display-preview.png','並べ替えと保存前の画面内プレビュー','Reorder frames and preview before saving'),('android-control-assignments.png','Androidで操作を変更・初期値復元','Change assignments or restore defaults on Android'),('web-control-assignments.png','Wi-Fi設定画面で操作を編集・保存','Edit and save assignments in Wi-Fi setup')]
for lang in ('ja','en'):
 intro='図解 → 対応環境の比較 → Android接続 → 表示編集 → 操作割り当て、の順で掲載。' if lang=='ja' else 'Suggested order: controls → compatibility → Android connection → display editor → control assignments.'
 body=f'<h1>CLumo — {copy[lang][0]}</h1><p>{intro}</p><p><a href="index.{"en" if lang=="ja" else "ja"}.html">日本語 / English</a></p><img class="diagram" src="diagrams/controls.{lang}.svg">'
 body+=md((O/('features.ja.md' if lang=='ja' else 'features.md')).read_text())
 body+='<h2>'+('設定画面' if lang=='ja' else 'Setup screens')+'</h2><div class="screens">'
 for name,ja,en in captions:body+=f'<figure><img src="screenshots/{name}"><figcaption>{ja if lang=="ja" else en}</figcaption></figure>'
 body+='</div><p>'+('スクリーンショットはAndroid実機の英語UI。Web画面もAndroid Chromeで撮影。アプリ内のLED図は装飾で、実音量の表示ではありません。エディターのオレンジは実物LEDの発光色ではありません。' if lang=='ja' else 'English UI captured on Android; the Web screen was captured in Android Chrome. LEDs in the app illustration are decorative, not actual-volume readings. Orange editor pixels do not specify the physical LED color.')+'</p>'
 (O/f'index.{lang}.html').write_text('<!doctype html><html lang="'+lang+'"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>CLumo features</title><style>body{max-width:1100px;margin:40px auto;padding:0 24px;font:16px/1.7 system-ui;color:#303435;background:#fafaf8}.diagram{width:100%}table{border-collapse:collapse;width:100%;font-size:14px}td{border:1px solid #ddd;padding:10px}tr:first-child{background:#e8eae7;font-weight:bold}.screens{display:flex;flex-wrap:wrap;gap:24px}figure{margin:0;width:300px}figure img{width:100%}figcaption{margin:8px 0}h2{margin-top:40px}</style>'+body+'</html>')
