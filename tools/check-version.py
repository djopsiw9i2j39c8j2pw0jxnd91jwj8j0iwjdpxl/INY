import os
import pathlib
import re

build = (pathlib.Path(__file__).resolve().parents[1] / 'app/build.gradle').read_text()
name = re.search(r"versionName\s+'(\d+\.\d{2}\.\d{2})'", build).group(1)
code = int(re.search(r'versionCode\s+(\d+)', build).group(1))
a, b, c = map(int, name.split('.'))
assert code == a * 100_000 + b * 1_000 + c, 'versionCode and versionName do not match'
tag = os.getenv('GITHUB_REF', '')
assert not tag.startswith('refs/tags/') or tag == f'refs/tags/v{name}', 'tag and versionName do not match'
print(f'INY {name} ({code})')
