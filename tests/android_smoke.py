"""Optional Android runtime smoke tests using adb and UIAutomator (no extra APK).

Use only on a test device: writes synthetic search history and preferences.
Usage: python tests/android_smoke.py --adb PATH --serial SERIAL
"""
import argparse
import re
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--adb', required=True)
parser.add_argument('--serial', required=True)
parser.add_argument('--reset', action='store_true', help='Clear this app on a disposable test device before running')
args = parser.parse_args()
ROOT = Path(__file__).resolve().parents[1]

def adb(*values):
    return subprocess.check_output([args.adb, '-s', args.serial, *values], text=True, encoding='utf-8', errors='replace', timeout=40)

def shell(*values):
    return adb('shell', shlex.join(values))

def tree():
    shell('uiautomator', 'dump', '/sdcard/nofeed-smoke.xml')
    return ET.fromstring(shell('cat', '/sdcard/nofeed-smoke.xml'))

def find(text=None, desc=None, cls=None):
    for node in tree().iter('node'):
        if text is not None and node.get('text') != text: continue
        if desc is not None and node.get('content-desc') != desc: continue
        if cls is not None and node.get('class') != cls: continue
        return node
    raise AssertionError(f'Missing UI: {text or desc or cls}')

def tap(text=None, desc=None, cls=None):
    node = find(text, desc, cls)
    x1,y1,x2,y2 = map(int, re.findall(r'\d+', node.get('bounds')))
    shell('input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
    time.sleep(.25)

def home(query=None):
    values = ['am','start','-W','-n','org.nofeed.android/.MainActivity']
    if query is not None: values += ['-a','android.intent.action.SEND','-t','text/plain','--es','android.intent.extra.TEXT', query]
    shell(*values)
    time.sleep(.3)

def back():
    shell('input','keyevent','4')
    time.sleep(.25)

if args.reset:
    shell('pm','clear','org.nofeed.android')
home('一搜 测试 & a+b')
find(text='一搜 测试 & a+b', desc='搜索关键词')
for label in ['豆瓣','淘宝','闲鱼']:
    tap(desc=label+'，点击选择')
    tap(desc='去'+label+'搜索')
    find(text='无法直达'+label)
    tap(text='返回')
print('PASS share input, 3 platform selections, missing-app fallback dialogs', flush=True)

tap(desc='更多应用')
tap(text='豆瓣')
find(desc='豆瓣，已选择')
tap(desc='搜索历史')
find(text='还没有搜索记录')
tap(desc='返回首页')
print('PASS more selection, empty history and return preserves query', flush=True)

tap(desc='去豆瓣搜索')
tap(text='用网页版搜索')
time.sleep(2)
back()
home()
tap(desc='搜索历史')
find(text='用「豆瓣」搜索「一搜 测试 & a+b」')
tap(text='清空历史记录')
tap(text='清空')
find(text='还没有搜索记录')
tap(desc='返回首页')
print('PASS explicit web fallback records history, clear history', flush=True)

tap(desc='设置')
tap(text='默认应用  ·  每次选择')
tap(text='淘宝')
find(text='默认应用  ·  淘宝')
tap(desc='返回首页')
shell('am','force-stop','org.nofeed.android')
home()
find(desc='淘宝，已选择')
tap(desc='设置')
find(text='默认应用  ·  淘宝')
# Removed controls must not reappear after upgrade or process restart.
texts = [n.get('text', '') for n in tree().iter('node')]
assert not any(t in ('别名', '应用别名', '搜索链接') for t in texts)
tap(text='默认应用  ·  淘宝')
tap(text='每次选择')
tap(desc='返回首页')
print('PASS default app, removed settings, preference persistence across process restart', flush=True)
print('Android smoke tests completed. Target apps themselves were not installed.', flush=True)
