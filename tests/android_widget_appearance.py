"""Disposable MuMu widget appearance regression. Does nothing without --run.

Build scripts/build-widget-fixture.py first. Requires ADB and Pillow on the test
machine only, not in the app. Run with --run --disposable-emulator; --adb and
--apk accept local paths. The fixed serial guards against targeting a real phone.

UI changes only the local appearance preference. It preserves app ordering,
restores the original system night and keyboard settings, revokes the fixture
binding grant, then uninstalls only the test host installed by this run.
"""
from pathlib import Path
import argparse
import re
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
HOST = 'org.nofeed.widgetfixture'
LABELS = {'system': '跟随系统', 'light': '浅色', 'dark': '深色'}
SURFACE = {False: (255, 255, 255), True: (32, 37, 31)}
FIELD = {False: (243, 245, 241), True: (39, 45, 38)}

class Regression:
    def __init__(self, args):
        self.args = args
        self.out = ROOT / 'build/widget-fixture/appearance'
        self.out.mkdir(parents=True, exist_ok=True)
    def adb(self, *values):
        return subprocess.check_output([str(self.args.adb), '-s', self.args.serial, *map(str, values)], encoding='utf-8', errors='replace', timeout=60)
    def sh(self, *values):
        return self.adb('shell', shlex.join(map(str, values))).strip()
    def nodes(self):
        for _ in range(3):
            self.sh('rm', '-f', '/sdcard/widget-appearance.xml')
            if 'dumped' in self.sh('uiautomator', 'dump', '/sdcard/widget-appearance.xml'):
                return list(ET.fromstring(self.sh('cat', '/sdcard/widget-appearance.xml')).iter('node'))
            time.sleep(.3)
        raise AssertionError('No fresh UI tree')
    @staticmethod
    def bounds(n):
        return tuple(map(int, re.findall(r'\d+', n.get('bounds'))))
    def find(self, text=None, desc=None, ns=None):
        for n in self.nodes() if ns is None else ns:
            if (text is None or n.get('text') == text) and (desc is None or n.get('content-desc') == desc):
                if self.bounds(n)[2] > self.bounds(n)[0]: return n
        raise AssertionError('Missing UI: '+str(text or desc))
    def tap_node(self, n):
        a, b, c, d = self.bounds(n)
        self.sh('input', 'tap', (a+c)//2, (b+d)//2)
        time.sleep(.5)
    def tap(self, text=None, desc=None):
        self.tap_node(self.find(text, desc))
    def screenshot(self, name):
        self.sh('screencap', '-p', '/sdcard/widget-appearance.png')
        target = self.out / name
        self.adb('pull', '/sdcard/widget-appearance.png', target)
        return Image.open(target).convert('RGB')
    def main(self):
        self.sh('am', 'start', '-W', '-f', '0x24000000', '-n', 'org.nofeed.android/.MainActivity')
        time.sleep(.4)
    def settings(self):
        self.main(); self.tap(desc='设置')
    def appearance_control(self, ns=None):
        return next(n for n in self.nodes() if n.get('content-desc', '').startswith('外观：')) if ns is None else next(n for n in ns if n.get('content-desc', '').startswith('外观：'))
    def current_mode(self):
        self.settings()
        desc = self.appearance_control().get('content-desc')
        return next(k for k, label in LABELS.items() if desc.startswith('外观：'+label))
    def set_mode(self, mode):
        self.settings(); self.tap_node(self.appearance_control()); self.tap(text=LABELS[mode]); time.sleep(.5)
        assert self.appearance_control().get('content-desc').startswith('外观：'+LABELS[mode])
    def order(self):
        self.main(); self.tap(desc='更多应用')
        result = [n.get('content-desc') for n in self.nodes() if n.get('content-desc', '').startswith('拖动排序：')]
        assert len(result) == 7
        self.tap(text='完成'); return result
    def host(self):
        self.sh('am', 'start', '-W', '-f', '0x24000000', '-n', HOST+'/fixture.MainActivity', '--ei', 'compact_height', 70, '--ei', 'widget_width', 300)
        time.sleep(.7)
    def existing_host(self):
        # Return through the task stack, preserving the same AppWidgetHostView.
        # Relaunching the fixture would construct a new view and hide reapply bugs.
        self.sh('input', 'keyevent', 'KEYCODE_BACK'); time.sleep(.4)
        self.sh('input', 'keyevent', 'KEYCODE_BACK'); time.sleep(.7)
        assert any(n.get('resource-id')=='org.nofeed.android:id/widget_root' for n in self.nodes()), 'Existing widget host must resume without re-render intent'
    def night(self, mode):
        result = self.sh('cmd', 'uimode', 'night', mode)
        assert 'Error' not in result, result
        time.sleep(1)
    @staticmethod
    def near(actual, expected):
        return max(abs(a-b) for a, b in zip(actual, expected)) <= 8
    def widget(self, dark, name):
        # Retry allows the protected configuration broadcast/launcher re-inflate.
        last = None
        for attempt in range(4):
            ns = self.nodes()
            root = next(n for n in ns if n.get('resource-id') == 'org.nofeed.android:id/widget_root')
            query = self.find(desc='输入关键词并选择搜索应用', ns=ns)
            a,b,c,d = self.bounds(root); qa,qb,qc,qd = self.bounds(query)
            density = (qd-qb)/36
            im = self.screenshot(name)
            surface = im.getpixel(((a+c)//2, b+max(2, round(density*2))))
            field = im.getpixel((qa+round(density*5), (qb+qd)//2))
            last = (surface, field)
            if self.near(surface, SURFACE[dark]) and self.near(field, FIELD[dark]):
                crop = im.crop((a,b,c,d))
                colorful = sum(max(p)-min(p)>55 and max(p)>115 for p in crop.getdata())
                assert colorful > crop.width*crop.height*.03, 'Brand icons must keep their original colors'
                apps = [n for n in ns if n.get('content-desc', '').endswith('，输入并搜索') and self.bounds(n)[2] > self.bounds(n)[0]]
                assert apps
                print('PASS widget '+name+' surface='+str(surface)+' field='+str(field), flush=True)
                return apps[0].get('content-desc').split('，', 1)[0]
            time.sleep(.6)
        raise AssertionError('Wrong widget appearance '+str(dark)+' '+str(last))
    def open_sheet(self, target):
        self.tap(desc=target+'，输入并搜索')
    def sheet(self, dark, target, name, draft=None):
        ns = self.nodes(); self.find(text=target+' ▾', ns=ns)
        query = self.find(desc='快捷搜索关键词', ns=ns)
        close = self.find(desc='关闭快捷搜索', ns=ns)
        qa,qb,qc,qd = self.bounds(query); ca,cb,cc,cd = self.bounds(close)
        panels = []
        for n in ns:
            if n.get('class') != 'android.widget.LinearLayout': continue
            a,b,c,d = self.bounds(n)
            if a<=qa and b<=qb and c>=qc and d>=qd and a<=ca and b<=cb and c>=cc and d>=cd:
                panels.append((a,b,c,d))
        a,b,c,d = min(panels, key=lambda p:(p[2]-p[0])*(p[3]-p[1]))
        density = (qd-qb)/56
        im = self.screenshot(name)
        surface = im.getpixel((a+round(6*density), b+round(26*density)))
        field = im.getpixel((qa+round(5*density),(qb+qd)//2))
        assert self.near(surface,SURFACE[dark]), ('Sheet surface', surface, SURFACE[dark])
        assert self.near(field,FIELD[dark]), ('Sheet field', field, FIELD[dark])
        if draft is not None: assert query.get('text')==draft, ('Draft was not preserved', query.get('text'))
        print('PASS sheet '+name+' target='+target+(' draft='+draft if draft else ''), flush=True)
    def close_sheet(self):
        self.tap(desc='关闭快捷搜索'); time.sleep(.4)
    def run(self):
        assert 'package:'+HOST not in self.sh('pm','list','packages',HOST).splitlines(), 'Test host already exists; refusing to overwrite it'
        assert self.args.apk.is_file()
        fixture = ROOT/'build/widget-fixture/fixture.apk'; assert fixture.is_file(), 'Build fixture first'
        system = re.search(r'Night mode:\s*(\w+)', self.sh('cmd','uimode','night')).group(1)
        assert system in ('no','yes','auto'), 'Unknown original night mode: '+system
        keyboard = self.sh('settings','get','secure','show_ime_with_hard_keyboard')
        installed = False; original = None; initial_order = None
        try:
            self.adb('install','-r',self.args.apk.resolve())
            original = self.current_mode(); initial_order = self.order()
            self.adb('install',fixture); installed = True
            self.sh('appwidget','grantbind','--package',HOST,'--user','0')
            # Hardware-keyboard mode keeps deterministic screenshots without IME.
            self.sh('settings','put','secure','show_ime_with_hard_keyboard','0')
            for mode, night, dark in [('dark','no',True),('light','yes',False)]:
                self.night(night); self.set_mode(mode); self.host()
                target = self.widget(dark, 'widget-'+mode+'-system-'+night+'.png')
                self.open_sheet(target); self.sheet(dark,target,'input-'+mode+'-system-'+night+'.png'); self.close_sheet()
                self.set_mode('system'); self.existing_host()
                self.widget(night=='yes','widget-'+mode+'-to-system-reapply.png')
            self.night('no'); self.set_mode('system'); self.host()
            target = self.widget(False,'widget-follow-light.png')
            # The main App stays in background during both following-system checks.
            self.night('yes'); target = self.widget(True,'widget-follow-dark.png')
            self.open_sheet(target); self.sheet(True,target,'input-follow-dark.png')
            self.sh('input','text','WidgetTheme'); self.sheet(True,target,'input-follow-dark-draft.png','WidgetTheme')
            self.night('no'); self.sheet(False,target,'input-follow-light-draft.png','WidgetTheme')
            self.night('yes'); self.sheet(True,target,'input-follow-dark-draft-restored.png','WidgetTheme')
            self.close_sheet(); self.host(); self.widget(True,'widget-follow-dark-return.png')
            print('PASS widget manual overrides, following-system update, selected target and draft recreation',flush=True)
        finally:
            # Restore through the same UI path instead of writing private preferences.
            try:
                self.night(system)
                if original is not None: self.set_mode(original)
                if initial_order is not None: assert self.order()==initial_order, 'Original order changed'
            finally:
                if keyboard=='null': self.sh('settings','delete','secure','show_ime_with_hard_keyboard')
                else: self.sh('settings','put','secure','show_ime_with_hard_keyboard',keyboard)
                if installed:
                    self.sh('appwidget','revokebind','--package',HOST,'--user','0')
                    self.adb('uninstall',HOST)
        print('Appearance widget regression complete; settings, binding and app order restored.',flush=True)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb',type=Path,default=ROOT/'.tools/sdk/platform-tools/adb.exe')
    parser.add_argument('--serial',default='127.0.0.1:16384')
    parser.add_argument('--apk',type=Path,default=ROOT/'dist/yisou-0.7.0.apk')
    parser.add_argument('--disposable-emulator',action='store_true')
    parser.add_argument('--run',action='store_true')
    args=parser.parse_args()
    if not args.run or not args.disposable_emulator or args.serial!='127.0.0.1:16384':
        raise SystemExit('Requires --run --disposable-emulator and fixed local MuMu serial 127.0.0.1:16384')
    Regression(args).run()

if __name__=='__main__': main()
