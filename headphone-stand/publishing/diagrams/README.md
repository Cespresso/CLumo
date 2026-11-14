# 操作・表示の図解

別チャットで整理した日英図解を取り込んだもの。初期操作、表示の役割、固定の長押し操作を説明する。図はCAD由来の説明用レンダリングで、全点灯は実際の音量値や表示内容を示さない。

- [日本語PNG](controls.ja.png) / [English PNG](controls.en.png)
- [日本語SVG](controls.ja.svg) / [English SVG](controls.en.svg)
- [出典と取り込み時ハッシュ](sources.json)
- [入力レンダリング](control-panel-render.png)

SVGは画像を埋め込んでおり、単体で表示できる。PNGは1800×1800。編集元は[`../../tools/build_feature_docs.py`](../../tools/build_feature_docs.py)。CLumoルートから標準Pythonで再生成でき、他リポジトリには依存しない。

```sh
python3 headphone-stand/tools/build_feature_docs.py
qlmanage -t -s 1800 -o /tmp headphone-stand/publishing/diagrams/controls.ja.svg headphone-stand/publishing/diagrams/controls.en.svg
cp /tmp/controls.ja.svg.png headphone-stand/publishing/diagrams/controls.ja.png
cp /tmp/controls.en.svg.png headphone-stand/publishing/diagrams/controls.en.png
```

元のCAD・Blenderシーンの編集はmaker-projects側で行う。元シーンは`nothing-headphone-accessory/publishing/makerworld/features/clumo-front-controls-matrix-red.blend`。図解の説明文・番号・レイアウトはCLumo内の生成スクリプトで編集する。
