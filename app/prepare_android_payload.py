#!/usr/bin/env python3
"""Prepare pinned Android runtime without executing npm install scripts."""
import base64, hashlib, json, sys, tarfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent
VERSION='0.156.1-termux.1'
SRI='sha512-LU0f2T4XqbA9XAZ2jR9aDAtdQ37iJguSrTrcBsEfFe761vFxVJdumkSoJzIYlpBZ/v6oO3IaHvdau3wgkEXDGw=='
archive=Path(sys.argv[1])
assert 'sha512-'+base64.b64encode(hashlib.sha512(archive.read_bytes()).digest()).decode()==SRI,'archive integrity mismatch'
mapping={'package/bin/codex.bin':'payload/libcodex.so','package/bin/codex-code-mode-host':'payload/libcodex-codehost.so','package/bin/libc++_shared.so':'payload/libc++_shared.so','package/LICENSE':'assets/CODEX-LICENSE','package/NOTICE':'assets/CODEX-NOTICE'}
original={}
with tarfile.open(archive) as tar:
 for member,path in mapping.items():
  data=tar.extractfile(member).read();original[path]=hashlib.sha256(data).hexdigest()
  (ROOT/path).parent.mkdir(parents=True,exist_ok=True);(ROOT/path).write_bytes(data)
  if path.startswith('payload/'):(ROOT/path).chmod(0o755)
# APK installation extracts lib*.so only; match the helper basename, as AGENTCODI does.
# Pin both the archive and an unambiguous nearby constant. Do not patch diagnostics.
p=ROOT/'payload/libcodex.so';data=p.read_bytes()
needle=b'codex-package.jsoncodex-code-mode-hostzshbincodex-resources'
assert data.count(needle)==1,'unexpected runtime layout'
offset=data.index(needle)+len(b'codex-package.json')
old=b'codex-code-mode-host';new=b'libcodex-codehost.so';assert len(old)==len(new)
data=data[:offset]+new+data[offset+len(old):];p.write_bytes(data)
legacy=ROOT/'payload/libcodex-code-mode-host.so'
if legacy.exists():legacy.unlink()
record={'package':'@mmmbuto/codex-cli-termux','version':VERSION,'source':'https://github.com/DioNanos/codex-termux','tarball':f'https://registry.npmjs.org/@mmmbuto/codex-cli-termux/-/codex-cli-termux-{VERSION}.tgz','integrity':SRI,'unmodifiedBinaries':False,'originalSha256':original,'patch':{'purpose':'APK-installed helper basename','offset':offset,'from':old.decode(),'to':new.decode()},'files':{v:hashlib.sha256((ROOT/v).read_bytes()).hexdigest() for v in mapping.values()}}
(ROOT/'assets/runtime-provenance.json').write_text(json.dumps(record,indent=2)+'\n')
(ROOT/'assets/ANDROID-INTEGRATION-NOTICE').write_text('Android Agent diagnostic integration: Codex Termux '+VERSION+'.\nThe app-server executable has one equal-length helper basename change from codex-code-mode-host to libcodex-codehost.so for APK nativeLibraryDir extraction. See runtime-provenance.json for exact archive integrity, byte offset, and original/modified SHA-256 hashes. No other executable bytes were changed.\n')
print('Verified archive and prepared Android payload; helper basename offset',offset)
