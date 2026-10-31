"""Appearance regression on an explicitly disposable local Android emulator.

Checks actual rendered system bars, pages and dialogs, manual overrides versus
system night mode, and state preservation across Activity recreation. Pillow is
used only to inspect test screenshots; it is not an application dependency.
Does not search, change history preferences or erase app data. Restores the
original system night setting, app appearance, input draft and selected target.
Manufacturer status bars and launchers still require their own device checks.
"""
import argparse
from pathlib import Path
import re
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET

from PIL import Image, ImageStat


parser = argparse.ArgumentParser()
parser.add_argument('--adb', required=True)
parser.add_argument('--serial', required=True)
parser.add_argument('--apk', required=True, type=Path)
parser.add_argument('--disposable-emulator', action='store_true')
args = parser.parse_args()
if not args.disposable_emulator or not args.serial.startswith(('127.0.0.1:', 'emulator-')):
    raise SystemExit('Requires an explicitly disposable local emulator')
if not args.apk.is_file():
    raise SystemExit('APK does not exist: ' + str(args.apk))
root = Path(__file__).resolve().parents[1]
output = root / 'build' / 'appearance'
output.mkdir(parents=True, exist_ok=True)
package = 'org.nofeed.android'
modes = ('跟随系统', '浅色', '深色')
targets = dict(zip(('豆瓣', '淘宝', '闲鱼', '微信', '哔哩哔哩', '拼多多', '小红书', '美团', '携程', '高德地图', '淘宝闪购'),
                   ('DOUBAN', 'TAOBAO', 'XIANYU', 'WECHAT', 'BILIBILI', 'PINDUODUO', 'XIAOHONGSHU', 'MEITUAN', 'CTRIP', 'AMAP', 'TAOBAO_SHANGOU')))


def adb(*values):
    return subprocess.check_output([args.adb, '-s', args.serial, *values],
                                   encoding='utf-8', errors='replace', timeout=60)


def shell(*values):
    return adb('shell', shlex.join(values)).strip()


def nodes():
    for _ in range(3):
        shell('rm', '-f', '/sdcard/yisou-appearance-test.xml')
        if 'dumped' in shell('uiautomator', 'dump', '/sdcard/yisou-appearance-test.xml'):
            return list(ET.fromstring(shell('cat', '/sdcard/yisou-appearance-test.xml')).iter('node'))
        time.sleep(.3)
    raise AssertionError('No fresh UI tree; refusing to reuse stale nodes')


def find(text=None, desc=None, current=None):
    matches = [n for n in (nodes() if current is None else current)
               if (text is None or n.get('text') == text) and (desc is None or n.get('content-desc') == desc)]
    if not matches:
        raise AssertionError('Missing UI: ' + str(text or desc))
    return matches[-1]


def bounds(node):
    return tuple(map(int, re.findall(r'\d+', node.get('bounds', ''))))


def tap_node(node):
    left, top, right, bottom = bounds(node)
    assert right > left and bottom > top, 'Control has no visible hit area'
    shell('input', 'tap', str((left + right) // 2), str((top + bottom) // 2))
    time.sleep(.4)


def tap(text=None, desc=None):
    tap_node(find(text=text, desc=desc))


def home():
    shell('am', 'start', '-W', '-f', '0x24000000', '-n', package + '/.MainActivity')
    time.sleep(.4)


def settings():
    if not any(n.get('content-desc', '').startswith('外观：') for n in nodes()):
        # Clear an optional open dialog before starting the current Activity.
        home()
        if not any(n.get('content-desc') == '设置' for n in nodes()):
            shell('input', 'keyevent', 'KEYCODE_BACK')
            home()
        tap(desc='设置')


def appearance_button(current=None):
    matches = [n for n in (nodes() if current is None else current)
               if n.get('content-desc', '').startswith('外观：')]
    assert len(matches) == 1, 'Settings must expose one explicit appearance selector'
    return matches[0]


def appearance_name():
    name = appearance_button().get('content-desc').split('：', 1)[1].split('，', 1)[0]
    assert name in modes, 'Unrecognized appearance value'
    return name


def choose_mode(name):
    settings()
    tap_node(appearance_button())
    tap(text=name)
    # Choosing a theme recreates the Activity; remaining on Settings is part of
    # the contract, so do not navigate back there on behalf of a broken build.
    for _ in range(4):
        current = nodes()
        assert not any('停止运行' in n.get('text', '') for n in current), 'App crashed while recreating for a theme change; inspect AndroidRuntime'
        button = next((n for n in current if n.get('content-desc', '').startswith('外观：')), None)
        if button is not None and ('外观：' + name + '，') in button.get('content-desc', ''):
            return
        time.sleep(.4)
    raise AssertionError('Theme change must keep Settings open with the saved value')


def night(value):
    shell('cmd', 'uimode', 'night', value)
    time.sleep(.8)


def capture(name):
    data = subprocess.check_output([args.adb, '-s', args.serial, 'exec-out', 'screencap', '-p'], timeout=60)
    path = output / (name + '.png')
    path.write_bytes(data)
    with Image.open(path) as image:
        return image.convert('RGB')


def color_at(image, x, y):
    x = min(max(3, int(x)), image.width - 4)
    y = min(max(3, int(y)), image.height - 4)
    return tuple(ImageStat.Stat(image.crop((x - 2, y - 2, x + 3, y + 3))).median)


def luminance(color):
    return .2126 * color[0] + .7152 * color[1] + .0722 * color[2]


def assert_text_contrast(image, node, dark):
    left, top, right, bottom = bounds(node)
    pixels = image.crop((left, top, right, bottom)).getdata()
    visible = sum(luminance(pixel) > 150 if dark else luminance(pixel) < 120 for pixel in pixels)
    assert visible > (right - left) * (bottom - top) * .005, 'Title text must remain visible against its surface'


def assert_settings_theme(dark, name):
    current = nodes()
    title = find(text='设置', current=current)
    image = capture(name)
    _, title_top, _, title_bottom = bounds(title)
    assert title_top >= 8, 'Expected a visible status-bar inset on this emulator'
    # Center of the system bar is blank on the emulator; the left page margin
    # avoids cards, text and controls. Median sampling avoids a single edge pixel.
    bar = color_at(image, image.width * .5, title_top * .5)
    page = color_at(image, image.width * .015, (title_top + title_bottom) * .5)
    assert max(abs(a - b) for a, b in zip(bar, page)) <= 5, ('Status bar differs from Settings background', bar, page)
    assert luminance(page) < 90 if dark else luminance(page) > 225, ('Unexpected Settings theme', page)
    assert_text_contrast(image, title, dark)


def assert_dialog_theme(title, dark, name):
    current = nodes()
    node = find(text=title, current=current)
    panel = next((n for n in current if n.get('resource-id') == 'android:id/parentPanel'), None)
    if panel is None:
        panel = next((n for n in current if n.get('resource-id') == 'android:id/contentPanel'), None)
    assert panel is not None, 'Expected a native AlertDialog panel'
    image = capture(name)
    left, top, right, bottom = bounds(panel)
    _, text_top, _, text_bottom = bounds(node)
    # Empty right-hand space beside the title, not the text or radio button.
    color = color_at(image, right - max(12, image.width * .02), (text_top + text_bottom) * .5)
    assert luminance(color) < 105 if dark else luminance(color) > 215, ('Unexpected dialog surface', color)
    assert_text_contrast(image, node, dark)


def restore_input(draft, target):
    shell('am', 'start', '-W', '-f', '0x24000000', '-n', package + '/.MainActivity',
          '-a', 'android.intent.action.SEND', '-t', 'text/plain',
          '--es', 'android.intent.extra.TEXT', draft, '--es', 'target', target)
    time.sleep(.4)


system_output = shell('cmd', 'uimode', 'night')
system_match = re.search(r'Night mode:\s*(auto|yes|no|custom)\b', system_output, re.I)
if system_match is None:
    raise SystemExit('Cannot read original system night mode; refusing to change it')
original_night = system_match.group(1).lower()
original_mode = original_draft = original_target = None
try:
    adb('install', '-r', str(args.apk.resolve()))
    home()
    current = nodes()
    editor = find(desc='搜索关键词', current=current)
    original_draft = editor.get('text', '')
    if original_draft == '搜索…':
        original_draft = ''
    search = next((n.get('content-desc', '') for n in current
                   if n.get('content-desc') == '搜索' or (n.get('content-desc', '').startswith('去') and n.get('content-desc', '').endswith('搜索'))), '搜索')
    original_target = targets.get(search[1:-2], '') if search.startswith('去') else ''
    settings()
    original_mode = appearance_name()
    default_before = next(n.get('content-desc') for n in nodes() if n.get('content-desc', '').startswith('默认应用：'))
    history_before = find(desc='保存搜索历史').get('checked')

    night('no')
    choose_mode('浅色')
    assert_settings_theme(False, 'settings-light-status-bar')
    tap_node(appearance_button())
    assert_dialog_theme('外观', False, 'appearance-dialog-light')
    tap(text='取消')
    choose_mode('深色')
    assert_settings_theme(True, 'settings-dark-manual-system-light')
    tap_node(appearance_button())
    assert_dialog_theme('外观', True, 'appearance-dialog-dark')
    tap(text='取消')
    default = next(n for n in nodes() if n.get('content-desc', '').startswith('默认应用：'))
    tap_node(default)
    assert_dialog_theme('默认应用', True, 'default-dialog-dark')
    tap(text='取消')
    tap(desc='返回首页')
    tap(desc='更多应用')
    assert_dialog_theme('应用', True, 'more-dialog-dark')
    tap(text='完成')
    print('PASS matching status bars, manual dark on a light system, and readable native dialogs', flush=True)

    night('yes')
    settings()
    choose_mode('浅色')
    assert_settings_theme(False, 'settings-light-manual-system-dark')
    choose_mode('跟随系统')
    assert_settings_theme(True, 'settings-system-dark')
    night('no')
    # UiMode should recreate the current page rather than send us to Home.
    assert_settings_theme(False, 'settings-system-light')
    assert appearance_name() == '跟随系统'
    print('PASS manual light on a dark system and following live system changes', flush=True)

    restore_input('appearance', 'DOUBAN')
    tap(desc='搜索关键词')
    shell('input', 'keyevent', 'KEYCODE_MOVE_END')
    shell('input', 'text', '-test')
    tap(desc='设置')
    choose_mode('深色')
    tap(desc='返回首页')
    assert find(desc='搜索关键词').get('text') == 'appearance-test', 'Theme recreation lost the edited draft or replayed the old share Intent'
    find(desc='去豆瓣搜索')
    shell('am', 'force-stop', package)
    home()
    settings()
    assert appearance_name() == '深色', 'Manual appearance must survive a process restart'
    assert_settings_theme(True, 'settings-dark-after-restart')
    default_after = next(n.get('content-desc') for n in nodes() if n.get('content-desc', '').startswith('默认应用：'))
    assert default_after == default_before, 'Appearance changed the default app'
    assert find(desc='保存搜索历史').get('checked') == history_before, 'Appearance changed the history preference'
    print('PASS recreation preserves the edited draft/target and restart preserves appearance without changing search preferences', flush=True)
finally:
    cleanup_errors = []
    try:
        night(original_night)
    except Exception as error:
        cleanup_errors.append('system night setting: ' + str(error))
    if original_mode is not None:
        try:
            settings()
            choose_mode(original_mode)
        except Exception as error:
            cleanup_errors.append('app appearance: ' + str(error))
    if original_draft is not None and original_target is not None:
        try:
            restore_input(original_draft, original_target)
        except Exception as error:
            cleanup_errors.append('input draft/target: ' + str(error))
    if cleanup_errors:
        raise RuntimeError('Test cleanup requires attention: ' + '; '.join(cleanup_errors))
print('Appearance checks passed; original settings and input restored. No search was submitted.', flush=True)
