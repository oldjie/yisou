"""Catalog UI regression on a disposable emulator; changes display order, not history.

Requires build-widget-fixture.py. Verifies real drag, missing-app artwork,
manual override/persistence/reset, the half-icon cue and widget synchronization.
"""
import argparse
from pathlib import Path
import re
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET
from PIL import Image

p=argparse.ArgumentParser()
p.add_argument('--adb',required=True);p.add_argument('--serial',required=True)
p.add_argument('--apk',required=True,type=Path);p.add_argument('--disposable-emulator',action='store_true')
a=p.parse_args()
if not a.disposable_emulator or not a.serial.startswith(('127.0.0.1:','emulator-')):
    raise SystemExit('Requires an explicitly disposable local emulator')
root=Path(__file__).resolve().parents[1];host='org.nofeed.widgetfixture'
labels=['豆瓣','淘宝','闲鱼','微信','哔哩哔哩','拼多多','小红书']
packages=['com.douban.frodo','com.taobao.taobao','com.taobao.idlefish','com.tencent.mm','tv.danmaku.bili','com.xunmeng.pinduoduo','com.xingin.xhs']
def adb(*v):return subprocess.check_output([a.adb,'-s',a.serial,*v],encoding='utf-8',errors='replace',timeout=60)
def sh(*v):return adb('shell',shlex.join(v)).strip()
def nodes():
    for _ in range(3):
        sh('rm','-f','/sdcard/catalog-test.xml')
        if 'dumped' in sh('uiautomator','dump','/sdcard/catalog-test.xml'):
            return list(ET.fromstring(sh('cat','/sdcard/catalog-test.xml')).iter('node'))
        time.sleep(.3)
    raise AssertionError('No fresh UI tree')
def bounds(n):return tuple(map(int,re.findall(r'\d+',n.get('bounds'))))
def center(n):
    x,y,x2,y2=bounds(n);return str((x+x2)//2),str((y+y2)//2)
def find(text=None,desc=None):return next(n for n in nodes() if (text is None or n.get('text')==text) and (desc is None or n.get('content-desc')==desc))
def tap(text=None,desc=None):
    sh('input','tap',*center(find(text,desc)));time.sleep(.4)
def capture(name):
    sh('screencap','-p','/sdcard/catalog-test.png');path=root/'build/widget-fixture'/name
    adb('pull','/sdcard/catalog-test.png',str(path));return path

def open_home():
    sh('am','start','-W','-f','0x24000000','-n','org.nofeed.android/.MainActivity');time.sleep(.4)
def menu_order():return [n.get('content-desc').split('：',1)[1] for n in nodes() if n.get('content-desc','').startswith('拖动排序：')]
def widget_order():return [n.get('content-desc').split('，',1)[0] for n in nodes() if n.get('content-desc','').endswith('，输入并搜索')]
def host_view():sh('am','start','-W','-f','0x24000000','-n',host+'/fixture.MainActivity');time.sleep(.6)

assert 'package:'+host not in sh('pm','list','packages',host).splitlines()
installed={name for name,pkg in zip(labels,packages) if 'package:'+pkg in sh('pm','list','packages',pkg).splitlines()}
expected=[n for n in labels if n in installed]+[n for n in labels if n not in installed]
missing=next((n for n in labels if n not in installed),None)
if missing is None:raise SystemExit('Requires at least one target absent to verify built-in missing-app artwork')
fixture=False
try:
    adb('install','-r',str(a.apk.resolve()));open_home();tap(desc='更多应用');tap(text='恢复默认')
    assert menu_order()==expected
    capture('catalog-default.png');tap(text='完成');ns=nodes()
    viewport=next(n for n in ns if n.get('class')=='android.widget.HorizontalScrollView');left,top,right,bottom=bounds(viewport)
    choices=[n for n in ns if any(n.get('content-desc','').startswith(name+'，') for name in labels)]
    assert len(choices)==5,'Home must expose four full items and a partial fifth'
    widths=[bounds(n)[2]-bounds(n)[0] for n in choices]
    assert .40 < widths[-1]/widths[0] < .60,(widths,'Fifth item must be approximately half-visible')
    capture('home-catalog.png');print('PASS installed-first default and four-and-a-half item viewport',flush=True)
    tap(desc='更多应用');before=menu_order();handles=[n for n in nodes() if n.get('content-desc','').startswith('拖动排序：')]
    sh('input','draganddrop',*center(handles[0]),*center(handles[2]),'1200');time.sleep(.5)
    custom=before[1:3]+before[:1]+before[3:]
    assert menu_order()==custom,'Actual long-press drag must change order'
    # Move an absent app above an installed one: explicit order overrides default priority.
    tap(desc='拖动排序：'+missing);tap(text='上移')
    at=custom.index(missing)
    if at>0:custom[at-1],custom[at]=custom[at],custom[at-1]
    assert menu_order()==custom
    capture('catalog-custom.png');tap(desc='选择应用：'+missing)
    ns=nodes();item=next(n for n in ns if n.get('content-desc')==missing+'，已选择');x,y,x2,y2=bounds(item)
    image=Image.open(capture('home-missing-icon.png')).convert('RGB')
    # The absent WeChat in our fixture environment must still show its green brand artwork.
    if missing=='微信':
        pixels=list(image.crop((x,y,x2,min(y2,y+int((x2-x)*.9)))).getdata())
        assert sum(g>r*1.3 and g>b*1.3 and g>110 for r,g,b in pixels)>len(pixels)*.1,'Missing app artwork must not become a neutral placeholder'
    sh('am','force-stop','org.nofeed.android');open_home();tap(desc='更多应用');assert menu_order()==custom;tap(text='完成')
    print('PASS drag reorder, absent-app override, persistent order and built-in missing icon',flush=True)
    adb('install',str(root/'build/widget-fixture/fixture.apk'));fixture=True
    sh('appwidget','grantbind','--package',host,'--user','0');host_view();assert widget_order()[0]==custom[0]
    tap(desc=custom[0]+'，输入并搜索');find(text=custom[0]+' ▾')
    assert not any('源码' in n.get('text','') or '许可' in n.get('text','') for n in nodes())
    capture('widget-input-clean.png');tap(desc='关闭快捷搜索')
    open_home();tap(desc='更多应用');tap(text='恢复默认');assert menu_order()==expected;tap(text='完成')
    host_view();assert widget_order()[0]==expected[0]
    print('PASS widget order synchronization, reset and clean input without source button',flush=True)
finally:
    if fixture:
        sh('appwidget','revokebind','--package',host,'--user','0');adb('uninstall',host)
print('Catalog checks passed; manufacturer desktop behavior remains device-dependent.',flush=True)
