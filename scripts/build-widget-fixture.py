"""Build a disposable system widget host, never included in user releases."""
import os
from pathlib import Path
import subprocess
import zipfile

root = Path(__file__).resolve().parents[1]
tools = root/'.tools/sdk/android-15'
platform = root/'.tools/sdk/android-35/android.jar'
jdk = Path(os.environ['JAVA_HOME'])/'bin'
out = root/'build/widget-fixture'
out.mkdir(parents=True, exist_ok=True)
for name in ['classes', 'dex']: (out/name).mkdir(exist_ok=True)
def run(*args): subprocess.run([str(a) for a in args], check=True)
run(tools/'aapt2.exe', 'link', '-o', out/'base.apk', '-I', platform, '--manifest', root/'tests/widget_fixture/AndroidManifest.xml', '--min-sdk-version', '26', '--target-sdk-version', '35')
run(jdk/'javac.exe', '-encoding', 'UTF-8', '-source', '8', '-target', '8', '-bootclasspath', str(platform)+';'+str(tools/'core-lambda-stubs.jar'), '-d', out/'classes', root/'tests/widget_fixture/MainActivity.java', root/'tests/widget_fixture/IconProbe.java', root/'app/src/main/java/org/nofeed/android/AppIconView.java')
run(jdk/'java.exe', '-cp', tools/'lib/d8.jar', 'com.android.tools.r8.D8', '--min-api', '26', '--lib', platform, '--output', out/'dex', *list((out/'classes').rglob('*.class')))
with zipfile.ZipFile(out/'base.apk', 'a') as z: z.write(out/'dex/classes.dex', 'classes.dex')
run(tools/'zipalign.exe', '-f', '4', out/'base.apk', out/'aligned.apk')
run(jdk/'java.exe', '-jar', tools/'lib/apksigner.jar', 'sign', '--ks', root/'.tools/development.p12', '--ks-pass', 'pass:android', '--ks-key-alias', 'development', '--out', out/'fixture.apk', out/'aligned.apk')
print(out/'fixture.apk')
