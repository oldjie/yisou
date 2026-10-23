"""Verify real search results with installed Douban/Xianyu apps, no login required.

First complete each target app's onboarding on a disposable test emulator.
This sends the public keyword '哈利波特'. Does not inject a return key.
Taobao's native emulator crash is documented separately in docs/TESTING.md.
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
args = p.parse_args()
output = Path(__file__).resolve().parents[1] / 'build' / 'result-tests'
output.mkdir(parents=True, exist_ok=True)

def adb(*values):
    return subprocess.check_output([args.adb, '-s', args.serial, *values], text=True, encoding='utf-8', errors='replace', timeout=40)

def shell(*values):
    return adb('shell', shlex.join(values))

def tree():
    result = shell('uiautomator', 'dump', '/sdcard/nofeed-results-test.xml')
    if 'dumped' not in result:
        raise RuntimeError('Could not inspect current UI; refusing to reuse stale XML')
    return ET.fromstring(shell('cat', '/sdcard/nofeed-results-test.xml'))

for target, package, proof in [('DOUBAN', 'com.douban.frodo', '哈利·波特与魔法石'),
                               ('XIANYU', 'com.taobao.idlefish', '哈利波特')]:
    shell('am', 'force-stop', 'org.nofeed.android')
    shell('am', 'start', '-W', '-f', '0x24000000', '-n', 'org.nofeed.android/.MainActivity',
          '-a', 'android.intent.action.SEND', '-t', 'text/plain',
          '--es', 'android.intent.extra.TEXT', '哈利波特', '--es', 'target', target)
    expected_action = '去' + ('豆瓣' if target == 'DOUBAN' else '闲鱼') + '搜索'
    button = next(n for n in tree().iter('node') if n.get('content-desc') == expected_action and n.get('clickable') == 'true')
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', button.get('bounds')))
    shell('input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
    for attempt in range(6):
        time.sleep(1)
        try:
            current = tree()
            texts = [n.get('text', '') + ' ' + n.get('content-desc', '') for n in current.iter('node')]
            if target == 'DOUBAN':
                passed = any(proof in t for t in texts)
            else:
                # The keyword alone could still mean an input page. Require
                # the actual result Activity AND rendered price/filter nodes.
                activities = shell('dumpsys', 'activity', 'activities')
                passed = ('search_implement.SearchResultActivity' in activities
                          and any('综合' in t for t in texts)
                          and any('元' in t or '包邮' in t for t in texts))
            if passed:
                (output / (target.lower()+'.xml')).write_text(ET.tostring(current, encoding='unicode'), encoding='utf-8')
                shell('screencap', '-p', '/sdcard/nofeed-results.png')
                adb('pull', '/sdcard/nofeed-results.png', str(output/(target.lower()+'.png')))
                print('PASS', package, 'rendered results without a return key', flush=True)
                break
        except RuntimeError:
            pass
    else:
        raise AssertionError('No rendered search results: ' + target)
print('Taobao: exported gateway resolved; end-to-end result verification pending due to platform native crash on this emulator.')
