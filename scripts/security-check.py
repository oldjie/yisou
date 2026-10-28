"""Fail publication on obvious credentials, local identifiers or capability drift.

This is a release guard, not a claim of a complete security audit. Never prints
matching secret contents. Optional --staged checks exactly the Git index too.
"""
import argparse
import hashlib
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET
from release_files import ROOT, collect

def check():
    files = collect()
    font = (ROOT/'app/src/main/res/font/noto_serif_sc_regular.otf').read_bytes()
    assert hashlib.sha256(font).hexdigest() == 'e8f396decc1f0963a016a989c3d8852e863d1350996f573860a80767c83a1cd3', 'Bundled font differs from reviewed upstream binary'
    icon_hashes = {'platform_douban.png': '873fedebaf8cdc832f4eb70d3f500d397fa5b6236ac20679dc39a936b9b77fc0', 'platform_taobao.png': 'e8bab27669864624f6714ebd2a9c406f6c6cddb350b85845292794cdd6549f61', 'platform_xianyu.png': 'c736e422845c495406d33375a4248c5bb12d91cffac90cf5a5eeed5c3c7e6ecd', 'platform_wechat.png': '63b3e7dd50d8e08308e1b19257ca527ebeeb97f3fce67f86549acd2d7966914a', 'platform_bilibili.png': 'd210b88eccad05224e09022a6d9f3eb5b67f65789f35e3e92d4d610ed2b86dc4', 'platform_pinduoduo.png': '125020950c137239022a4e369ce11cc859898ffaceef559656acd8b766ff191a', 'platform_xiaohongshu.png': '45a48b9c69de013fa1bad5e3bf36a55593061dbf57c9c030094435df2ca5bbdb'}
    for name, digest in icon_hashes.items():
        assert hashlib.sha256((ROOT/"app/src/main/res/drawable-nodpi"/name).read_bytes()).hexdigest() == digest, "Bundled platform icon differs from reviewed asset: "+name
    license_text = (ROOT/'LICENSE').read_text(encoding='utf-8')
    assert 'GNU AFFERO GENERAL PUBLIC LICENSE' in license_text and '13. Remote Network Interaction' in license_text
    assert (ROOT/'app/src/main/res/raw/agpl_3.txt').read_text(encoding='utf-8') == license_text
    patterns = {
        'private key': r'-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----',
        'GitHub token': r'\b(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{30,})\b',
        'AWS access key': r'\b(?:AKIA|ASIA)[A-Z0-9]{16}\b',
        'Slack token': r'\bxox[baprs]-[A-Za-z0-9-]{20,}\b',
        'personal path': r'(?i)\b[A-Z]:[\\/](?:Users[\\/][A-Za-z0-9_.-]+|code[\\/][A-Za-z0-9_.-]+)',
        'WeChat account path': r'\bwxid_[A-Za-z0-9_]{5,}',
        'private mail': r'\b[0-9]{5,12}@qq\.com\b',
    }
    failures = []
    for name in files:
        if Path(name).suffix in {'.png', '.jar', '.otf'}:
            continue
        source = (ROOT/name).read_text(encoding='utf-8-sig')
        for label, pattern in patterns.items():
            for match in re.finditer(pattern, source):
                line = source.count('\n', 0, match.start())+1
                failures.append(f'{name}:{line}: {label}')
    android = '{http://schemas.android.com/apk/res/android}'
    manifest = ET.parse(ROOT/'app/src/main/AndroidManifest.xml').getroot()
    assert not manifest.findall('uses-permission'), 'Review new runtime permissions before publishing'
    app = manifest.find('application')
    assert app.get(android+'allowBackup') == 'false'
    assert app.get(android+'fullBackupContent') == 'false'
    sheet = app.find("activity[@"+android+"name='.WidgetSearchActivity']")
    assert sheet is not None and sheet.get(android+'exported') == 'false'
    adapter = app.find("service[@"+android+"name='.WidgetAppsService']")
    assert adapter is not None and adapter.get(android+'permission') == 'android.permission.BIND_REMOTEVIEWS'
    assert adapter.get(android+'exported') == 'false'
    for receiver in app.findall('receiver'):
        assert receiver.get(android+'exported') == 'false'
    service = app.find("service[@"+android+"name='.WechatSearchService']")
    assert service is not None and service.get(android+'permission') == 'android.permission.BIND_ACCESSIBILITY_SERVICE'
    config = ET.parse(ROOT/'app/src/main/res/xml/wechat_accessibility.xml').getroot()
    assert config.get(android+'canRetrieveWindowContent') == 'false'
    assert config.get(android+'canPerformGestures') == 'true'
    implementation = (ROOT/'app/src/main/java/org/nofeed/android/WechatSearchService.java').read_text(encoding='utf-8-sig')
    assert not re.search(r'getRootInActiveWindow|getWindows\(|getPrimaryClip\(', implementation)
    if failures:
        raise SystemExit('Publication check failed (values redacted):\n'+'\n'.join(failures))
    return files

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--staged', action='store_true')
    args = parser.parse_args()
    files = check()
    if args.staged:
        staged = subprocess.check_output(['git', 'ls-files', '-z'], cwd=ROOT).decode('utf-8').split('\0')
        staged = {name for name in staged if name}
        assert staged == set(files), 'Git index differs from reviewed release allowlist'
        for name in staged:
            blob = subprocess.check_output(['git', 'show', ':'+name], cwd=ROOT)
            local = (ROOT/name).read_bytes()
            if Path(name).suffix not in {'.png', '.jar', '.otf'}:
                blob = blob.replace(b'\r\n', b'\n'); local = local.replace(b'\r\n', b'\n')
            assert blob == local, 'Index differs from reviewed file: '+name
    print(f'PASS: {len(files)} reviewed source files; credential/path scan and Android capability guards')
