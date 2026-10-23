"""Fetch pinned official Android SDK packages, checking repository-provided SHA-1.

Read https://developer.android.com/studio/terms before using the SDK.
Python 3 standard library only. Extracts inside this project's ignored .tools/sdk.
"""
import hashlib
import io
from pathlib import Path
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PACKAGES = {
    "platform-35_r02.zip": "0bb560a90a7a2cbd0dd8348224d518b638fe7949",
    "build-tools_r35_windows.zip": "af059bb67cf7786f45ee0db85e2d24985df1b4b6",
    "platform-tools_r37.0.1-win.zip": "e03e78b1d80b396f1c3358e31251cb31740e1110",
}

def main():
    destination = ROOT / ".tools" / "sdk"
    destination.mkdir(parents=True, exist_ok=True)
    for name, expected in PACKAGES.items():
        print("Downloading", name, flush=True)
        with urllib.request.urlopen("https://dl.google.com/android/repository/" + name, timeout=180) as response:
            data = response.read()
        if hashlib.sha1(data).hexdigest() != expected:
            raise RuntimeError("Checksum mismatch: " + name)
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            for info in archive.infolist():
                if not (destination / info.filename).resolve().is_relative_to(destination.resolve()):
                    raise RuntimeError("Unsafe archive path")
            archive.extractall(destination)
    print("SDK ready:", destination)

if __name__ == "__main__":
    main()
