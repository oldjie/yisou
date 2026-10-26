"""Coordinate assistant regression on a disposable emulator, never real WeChat.

Supply a paste-menu center observed in this fixture's screenshot. Inspect the
fixture only while no assistant task is running: UIAutomator can suppress services.
Restores accessibility settings and uninstalls the clearly labelled fixture.
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
p.add_argument('--disposable-emulator', action='store_true')
p.add_argument('--paste-x', type=int, required=True)
p.add_argument('--paste-y', type=int, required=True)
p.add_argument('--reset', action='store_true', help='Clear Yisou data on this disposable emulator')
p.add_argument('--baseline-apk', type=Path, help='Optional old APK to reproduce the premature long-press failure')
p.add_argument('--updated-apk', type=Path, help='New APK installed over the baseline, preserving calibration')
a = p.parse_args()
if bool(a.baseline_apk) != bool(a.updated_apk): p.error('Supply both baseline and updated APKs, or neither')
root = Path(__file__).resolve().parents[1]
service = 'org.nofeed.android/.WechatSearchService'

def adb(*args):
    return subprocess.check_output([a.adb, '-s', a.serial, *args], encoding='utf-8', errors='replace', timeout=35)
def shell(*args): return adb('shell', shlex.join(args)).strip()
def nodes():
    shell('rm', '-f', '/sdcard/yisou-coordinate-test.xml')
    shell('uiautomator', 'dump', '/sdcard/yisou-coordinate-test.xml')
    return list(ET.fromstring(shell('cat', '/sdcard/yisou-coordinate-test.xml')).iter('node'))
def center(n):
    x1,y1,x2,y2 = map(int, re.findall(r'\d+', n.get('bounds')))
    return (x1+x2)//2, (y1+y2)//2

def find(text=None, desc=None, cls=None):
    for n in nodes():
        if text is not None and n.get('text') != text: continue
        if desc is not None and n.get('content-desc') != desc: continue
        if cls is not None and n.get('class') != cls: continue
        return n
    raise AssertionError('Missing UI: '+str(text or desc or cls))
def tap_xy(x,y): shell('input','tap',str(x),str(y))
def tap(**filters): tap_xy(*center(find(**filters)))
def home(query):
    shell('am', 'start', '-W', '-f', '0x24000000', '-n', 'org.nofeed.android/.MainActivity',
          '-a','android.intent.action.SEND','-t','text/plain','--es','android.intent.extra.TEXT',query,'--es','target','WECHAT')
def fixture(scenario):
    shell('am','force-stop','com.tencent.mm')
    shell('am','start','-W','-n','com.tencent.mm/fixture.MainActivity','--es','scenario',scenario)
def restore(key, value):
    if value == 'null': shell('settings','delete','secure',key)
    else: shell('settings','put','secure',key,value)
def enable():
    previous_list = [] if previous == 'null' else previous.split(':')
    shell('settings','put','secure','enabled_accessibility_services',':'.join(previous_list+[service]))
    shell('settings','put','secure','accessibility_enabled','1')
    time.sleep(2)
def result(query):
    texts = [n.get('text') for n in nodes()]
    assert 'RESULT:'+query in texts, texts

if not a.disposable_emulator or not a.serial.startswith(('127.0.0.1:', 'emulator-')):
    raise SystemExit('Requires explicit disposable emulator confirmation')
if 'package:com.tencent.mm' in shell('pm','list','packages','com.tencent.mm').splitlines():
    raise SystemExit('Refusing to overwrite existing WeChat or fixture')
previous = shell('settings','get','secure','enabled_accessibility_services')
enabled = shell('settings','get','secure','accessibility_enabled')
try:
    adb('install', str(root/'build/wechat-fixture/fixture.apk'))
    if a.baseline_apk: adb('install', '-r', str(a.baseline_apk.resolve()))
    if a.reset: shell('pm','clear','org.nofeed.android')
    fixture('normal')
    open_point = center(find(desc='搜索'))
    fixture('prepare')
    input_point = center(find(cls='android.widget.EditText'))
    submit_point = center(find(text='搜一搜'))
    fixture('normal')
    home('权限测试')
    tap(desc='去微信搜索')
    find(text='开启微信搜索辅助')
    tap(text='去开启')
    assert any(n.get('package') == 'com.android.settings' for n in nodes()), 'Public accessibility settings did not open'
    shell('input','keyevent','4')
    find(desc='去微信搜索')
    print('PASS permission button opens public system settings and returns without losing input',flush=True)
    enable()
    query='坐标校准 & a+b# 🐈'
    home(query)
    tap(desc='去微信搜索')
    # No UIAutomation connection while the service is performing gestures.
    time.sleep(3.8); tap_xy(*open_point)
    time.sleep(2.2); tap_xy(*input_point)
    time.sleep(4.6)
    shell('screencap','-p','/sdcard/yisou-before-paste.png')
    adb('pull','/sdcard/yisou-before-paste.png',str(root/'build/wechat-fixture/before-paste.png'))
    tap_xy(a.paste_x,a.paste_y)
    time.sleep(2.2)
    shell('screencap','-p','/sdcard/yisou-after-paste.png')
    adb('pull','/sdcard/yisou-after-paste.png',str(root/'build/wechat-fixture/after-paste.png'))
    tap_xy(*submit_point)
    time.sleep(2)
    result(query)
    print('PASS four calibration taps, native clipboard paste and search submission',flush=True)
    if a.baseline_apk:
        fixture('settling'); query='旧版过早长按复现'; home(query); tap(desc='去微信搜索')
        time.sleep(4)
        baseline_nodes = nodes()
        assert any(n.get('class') == 'android.widget.EditText' and n.get('text') in ('', '搜索') for n in baseline_nodes), [n.get('text') for n in baseline_nodes]
        assert not any(n.get('text') == 'RESULT:'+query for n in baseline_nodes)
        print('PASS baseline reproduced: editor remains empty when the paste menu was not ready',flush=True)
        restore('enabled_accessibility_services', previous)
        restore('accessibility_enabled', enabled)
        adb('install', '-r', str(a.updated_apk.resolve()))
        print('Installed update without clearing saved calibration',flush=True)
    # Restart the app/service, then replay positions without any calibration taps.
    restore('enabled_accessibility_services', previous)
    restore('accessibility_enabled', enabled)
    shell('am','force-stop','org.nofeed.android')
    fixture('normal')
    timings=[]
    for index in range(3):
        query='重启后自动搜索 + 第'+str(index+1)+'次 🐈'
        home(query)
        if index == 0: enable()
        button=center(find(desc='去微信搜索'))
        started=float(shell('cat','/proc/uptime').split()[0])*1000
        tap_xy(*button)
        time.sleep(5)
        result_nodes=nodes()
        assert any(n.get('text')=='RESULT:'+query for n in result_nodes), [n.get('text') for n in result_nodes]
        completed=next(int(n.get('text').split(':')[1]) for n in result_nodes if n.get('text','').startswith('FIXTURE_RESULT_UPTIME:'))
        timings.append(round(completed-started))
    print('PASS restart and repeated fresh keyword searches; click-to-fixture-result milliseconds: '+str(timings),flush=True)
    fixture('settling')
    for query in ['新词', '换一个更长的关键词 & a+b# 🐈', 'Harry Potter 2026']:
        home(query); tap(desc='去微信搜索'); time.sleep(5); result(query)
    print('PASS three changed keywords with delayed editor readiness and saved coordinates',flush=True)
    fixture('normal'); home('离开后不能搜索'); tap(desc='去微信搜索')
    time.sleep(.5); shell('input','keyevent','3'); time.sleep(1)
    shell('am','start','-W','-n','com.tencent.mm/fixture.MainActivity')
    time.sleep(10)
    find(desc='搜索')
    assert not any((n.get('text') or '').startswith('RESULT:') for n in nodes())
    print('PASS leaving WeChat cancels pending coordinate playback',flush=True)
finally:
    restore('enabled_accessibility_services', previous)
    restore('accessibility_enabled', enabled)
    shell('am','force-stop','com.tencent.mm')
    adb('uninstall','com.tencent.mm')
print('Coordinate fixture checks passed; real WeChat 8.0.78 remains unverified.',flush=True)
