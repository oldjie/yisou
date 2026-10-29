"""Create a source archive without caches, local settings, signing keys or references."""
from pathlib import Path
import zipfile
from release_files import collect

root = Path(__file__).resolve().parents[1]
output = root / 'dist' / 'yisou-0.6.1-source.zip'
output.parent.mkdir(exist_ok=True)
files = collect()
with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as archive:
    for name in sorted(set(files)):
        archive.write(root/name, 'yisou/'+name.replace('\\', '/'))
print(output)
