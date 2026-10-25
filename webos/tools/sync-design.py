"""Copy dimensions and vector paths directly from the Android design source."""
from pathlib import Path
import xml.etree.ElementTree as ET
import json
root=Path(__file__).resolve().parents[2]
ns='{http://schemas.android.com/apk/res/android}'
values=ET.parse(root/'app/src/main/res/values/dimens.xml').getroot()
lines=['/* Generated from Android dimens.xml at 2 px per dp (1920 × 1080). */',':root {']
for e in values:
    if e.tag=='dimen' and e.text.endswith('dp'):
        lines.append('  --'+e.attrib['name'].replace('_','-')+': '+str(float(e.text[:-2])*2).removesuffix('.0')+'px;')
lines+=['}']
(root/'webos/app/design-tokens.css').write_text('\n'.join(lines)+'\n')
icons={}
for name in ['play','pause','restart','rewind','forward','next','quality','speed','jump','play_tiny','back_chevron','search','search_clear','search_mic','watched_check']:
    p=root/f'app/src/main/res/drawable/ic_{name}.xml'
    if not p.exists():continue
    v=ET.parse(p).getroot()
    paths=[]
    for e in v.iter('path'):
        attrs={'d':e.get(ns+'pathData'),'fill':'currentColor' if e.get(ns+'fillColor','#FFFFFF') not in ['#00000000','@android:color/transparent'] else 'none'}
        for a,b in [('strokeWidth','stroke-width'),('strokeLineCap','stroke-linecap'),('strokeLineJoin','stroke-linejoin')]:
            if e.get(ns+a):attrs[b]=e.get(ns+a)
        if e.get(ns+'strokeColor'):attrs['stroke']='currentColor'
        if e.get(ns+'fillType')=='evenOdd':attrs['fill-rule']='evenodd'
        paths.append('<path '+' '.join(f'{k}="{val}"' for k,val in attrs.items())+'/>')
    icons[name]=f'<svg viewBox="0 0 {v.get(ns+"viewportWidth","24")} {v.get(ns+"viewportHeight","24")}" aria-hidden="true">'+''.join(paths)+'</svg>'
(root/'webos/app/icons.js').write_text('// Generated from Android vector resources.\nwindow.ITVIcons='+json.dumps(icons,ensure_ascii=False)+';\n')
