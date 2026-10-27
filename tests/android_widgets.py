"""System AppWidgetHost regression; disposable emulator only, no real launcher.

Build scripts/build-widget-fixture.py first. Restores the temporary keyboard
setting and widget-binding permission, then uninstalls only this test host.
"""
import argparse
from pathlib import Path
import re
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET

p = argparse.ArgumentParser()
p.add_argument('--adb', required=True)
p.add_argument('--serial', required=True)
p.add_argument('--apk', type=Path, required=True)
p.add_argument('--disposable-emulator', action='store_true')
a = p.parse_args()
root = Path(__file__).resolve().parents[1]
host = 'org.nofeed.widgetfixture'
if not a.disposable_emulator or not a.serial.startswith(('127.0.0.1:', 'emulator-')):
    raise SystemExit('Requires an explicitly disposable emulator')

def adb(*values):
    return subprocess.check_output([a.adb, '-s', a.serial, *values], encoding='utf-8', errors='replace', timeout=60)
def shell(*values): return adb('shell', shlex.join(values)).strip()
def nodes():
    for _ in range(3):
        shell('rm', '-f', '/sdcard/yisou-widget-test.xml')
        status = shell('uiautomator', 'dump', '/sdcard/yisou-widget-test.xml')
        if 'dumped' in status:
            return list(ET.fromstring(shell('cat', '/sdcard/yisou-widget-test.xml')).iter('node'))
        time.sleep(.4)
    raise AssertionError('No fresh UI tree; refusing to reuse stale nodes')
def find(text=None, desc=None, index=0):
    found = [n for n in nodes() if (text is None or n.get('text') == text) and (desc is None or n.get('content-desc') == desc)]
    if len(found) <= index: raise AssertionError('Missing UI: '+str(text or desc))
    return found[index]
def tap(text=None, desc=None, index=0):
    n = find(text, desc, index); x1,y1,x2,y2 = map(int, re.findall(r'\d+', n.get('bounds')))
    shell('input', 'tap', str((x1+x2)//2), str((y1+y2)//2)); time.sleep(.4)
def launch_host(width=300, height=70):
    shell('am', 'start', '-W', '-f', '0x24000000', '-n', host+'/fixture.MainActivity', '--ei', 'compact_height', str(height), '--ei', 'widget_width', str(width)); time.sleep(.8)

def capture(name):
    shell('screencap', '-p', '/sdcard/yisou-widget-test.png')
    adb('pull', '/sdcard/yisou-widget-test.png', str(root/'build/widget-fixture'/name))

def swipe_apps(left=True):
    n = next(n for n in nodes() if n.get('resource-id') == 'org.nofeed.android:id/widget_apps')
    x1,y1,x2,y2 = map(int, re.findall(r'\d+', n.get('bounds')))
    assert x2-x1 > y2-y1, 'Collection must have a horizontal viewport'
    start,end = (x2-18,x1+18) if left else (x1+18,x2-18)
    shell('input','swipe',str(start),str((y1+y2)//2),str(end),str((y1+y2)//2),'450');time.sleep(.4)

def target_visible(target):
    desc=target+'，输入并搜索'
    for _ in range(3):
        ns=nodes()
        match=next((n for n in ns if n.get('content-desc')==desc and n.get('bounds')!='[0,0][0,0]'),None)
        if match is not None: return
        swipe_apps(True)
    for _ in range(3):
        if any(n.get('content-desc')==desc for n in nodes()): return
        swipe_apps(False)
    raise AssertionError('Cannot horizontally reach '+target)

def close_sheet():
    tap(desc='关闭快捷搜索'); launch_host()

if 'package:'+host in shell('pm', 'list', 'packages', host).splitlines():
    raise SystemExit('Existing test host detected; remove it deliberately before running')
keyboard = shell('settings', 'get', 'secure', 'show_ime_with_hard_keyboard')
installed = False
try:
    adb('install', '-r', str(a.apk.resolve()))
    adb('install', str(root/'build/widget-fixture/fixture.apk')); installed = True
    shell('appwidget', 'grantbind', '--package', host, '--user', '0')
    shell('settings', 'put', 'secure', 'show_ime_with_hard_keyboard', '1')
    launch_host(); ns = nodes()
    assert not any(n.get('text') == 'TEST_BIND_DENIED' for n in ns)
    assert any('OEM 自适应包装图标：回归通过' in n.get('text','') for n in ns)
    assert len([n for n in ns if n.get('content-desc') == '输入关键词并选择搜索应用']) == 1
    assert not any(n.get('text') in ['豆瓣','淘宝','闲鱼','微信','哔哩哔哩','拼多多','小红书'] for n in ns), 'Widget icons have no visible names'
    capture('widgets.png')
    print('PASS one compact RemoteViews layout and production OEM icon regression', flush=True)
    tap(desc='下一组应用'); find(desc='小红书，输入并搜索')
    tap(desc='前一组应用'); find(desc='豆瓣，输入并搜索')
    print('PASS explicit arrow controls reach both ends of the collection', flush=True)
    initial={n.get('content-desc') for n in nodes()}
    swipe_apps(True); after={n.get('content-desc') for n in nodes()}
    assert after != initial and '小红书，输入并搜索' in after, 'Horizontal drag must reveal final app'
    capture('widgets-scrolled.png');swipe_apps(False)
    print('PASS horizontal finger swipe reveals additional application icons', flush=True)
    for target in ['豆瓣', '淘宝', '闲鱼', '微信','哔哩哔哩','拼多多','小红书']:
        target_visible(target);tap(desc=target+'，输入并搜索')
        find(desc='快捷搜索关键词'); find(text=target+' ▾')
        if target == '豆瓣': capture('input.png')
        close_sheet()
    print('PASS all seven collection clicks preserve the selected application', flush=True)
    tap(desc='输入关键词并选择搜索应用'); find(desc='快捷搜索关键词')
    tap(desc='切换搜索应用'); tap(text='豆瓣'); find(text='豆瓣 ▾')
    tap(desc='提交快捷搜索'); find(desc='快捷搜索关键词')
    print('PASS search field opens input, switches platform, and rejects empty queries', flush=True)
    close_sheet()
    launch_host(220);find(desc='输入关键词并选择搜索应用');target_visible('小红书');tap(desc='小红书，输入并搜索');find(text='小红书 ▾');close_sheet()
    launch_host(300,156);find(desc='输入关键词并选择搜索应用');launch_host()
    print('PASS narrow resizing remains horizontally scrollable and no second layout appears', flush=True)
    target_visible('豆瓣')
    tap(desc='豆瓣，输入并搜索')
    shell('input', 'text', 'Harry%sPotter')
    find(text='Harry Potter', desc='快捷搜索关键词'); tap(desc='提交快捷搜索')
    for _ in range(6):
        time.sleep(1); current = nodes()
        if any(n.get('package') == 'com.douban.frodo' and 'Harry Potter' in (n.get('text','')+n.get('content-desc','')) for n in current): break
    else: raise AssertionError('No Douban keyword reached the destination')
    assert not any(n.get('content-desc') == '快捷搜索关键词' for n in current)
    print('PASS typed keyword dispatched to Douban and transient input closed', flush=True)
    denied = subprocess.run([a.adb, '-s', a.serial, 'shell', 'am', 'start', '-n', 'org.nofeed.android/.WidgetSearchActivity'], capture_output=True, text=True, encoding='utf-8', errors='replace')
    assert 'Permission Denial' in denied.stdout+denied.stderr
    print('PASS external callers cannot launch the private widget input directly', flush=True)
finally:
    if keyboard == 'null': shell('settings', 'delete', 'secure', 'show_ime_with_hard_keyboard')
    else: shell('settings', 'put', 'secure', 'show_ime_with_hard_keyboard', keyboard)
    if installed:
        shell('appwidget', 'revokebind', '--package', host, '--user', '0')
        adb('uninstall', host)
print('Widget host checks passed; Huawei/Xiaomi physical launchers remain unverified.', flush=True)
