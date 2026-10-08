"""Validate the report's illustrative data and date arithmetic, not Mosaic behavior."""
import calendar
import hashlib
import json
import subprocess
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

def expiry(value):
    d = date.fromisoformat(value)
    total = d.year * 12 + d.month - 1 + 18
    year, month0 = divmod(total, 12)
    month = month0 + 1
    return date(year, month, min(d.day, calendar.monthrange(year, month)[1]))

def main():
    registry = json.loads((ROOT / 'registry.example.json').read_text())
    assert registry['exampleOnly'] is True
    as_of = date(2026, 10, 7)
    states = []
    for combo in registry['combinations']:
        assert combo['runtime'] in registry['releases']['runtime']
        assert combo['compiler'] in registry['kotlinReleases']
        if combo['analysis'] is not None:
            assert combo['analysis'] in registry['releases']['analysis']
        if combo['verdict'] == 'certified':
            assert combo['evidence'] and combo['certifiedAt']
            assert all(registry['evidence'][e]['result'] == 'pass' for e in combo['evidence'])
            family = registry['kotlinReleases'][combo['compiler']]['family']
            until = expiry(registry['kotlinFamilies'][family]['releasedOn'])
            state = 'active' if as_of < until else 'expired'
        else:
            state = combo['verdict']
        states.append({'id':combo['id'], 'state':state})
    assert set(x['state'] for x in states) == {'active','expired','not-certified','incompatible'}
    assert expiry('2025-06-23') == date(2026,12,23)
    assert expiry('2025-12-16') == date(2027,6,16)
    assert expiry('2026-06-03') == date(2027,12,3)
    inventory = [json.loads(x) for x in (ROOT/'research-evidence/official/kotlin-github-releases.jsonl').read_text().splitlines()]
    stable = {x['tag_name'].removeprefix('v') for x in inventory if not x['draft'] and not x['prerelease'] and date.fromisoformat(x['published_at'][:10]) <= as_of}
    expected = {'2.2.0','2.2.10','2.2.20','2.2.21','2.3.0','2.3.10','2.3.20','2.3.21','2.4.0','2.4.10','2.4.20'}
    assert stable == expected, (stable, expected)
    root = json.loads((ROOT/'research-evidence/repository/baseline.json').read_text())
    assert root['commit'] == 'cf490954c8e669c48c4263b36214b641fb9ce9f4'
    for path, sha in root['source_sha256'].items():
        content = subprocess.check_output(['git', 'show', root['commit'] + ':' + path], cwd=ROOT)
        assert hashlib.sha256(content).hexdigest() == sha, path
    result = {'scope':'Illustration, date arithmetic, source integrity and release inventory only; no runtime/analysis certification', 'baseline':'2026-10-07', 'stableVersions':sorted(stable,key=lambda s:tuple(map(int,s.split('.')))), 'statuses':states, 'checks':'passed'}
    (ROOT/'research-evidence/design-validation.json').write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result,indent=2))

if __name__ == '__main__':
    main()
