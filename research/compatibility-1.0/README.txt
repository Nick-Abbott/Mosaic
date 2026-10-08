Mosaic 1.0 compatibility architecture — report package

Start with REPORT.txt. This is an implementation-ready proposal based on develop
cf490954c8e669c48c4263b36214b641fb9ce9f4, not an implemented or certified feature.
The authoritative public compatibility contract is unchanged.

Contents
  REPORT.txt                              Architecture, diagnostics and PR sequence
  registry.example.json                   Fictional registry illustration
  research-evidence/prior/REPORT.txt       Previous compiler-compatibility research
  research-evidence/prior/*.json           Prior test counts and semantic comparisons
  research-evidence/official/              Official inventory and source provenance
  research-evidence/repository/baseline.json Immutable repository/file identities
  research-tools/check-design.py           Reproducible report data checks
  SHA256SUMS                              Checksums of this committed package

Validation from the repository root:
  python3 research/compatibility-1.0/research-tools/check-design.py
  (cd research/compatibility-1.0 && sha256sum --check SHA256SUMS)

These checks cover illustrative states, date arithmetic, exact release inventory
and baseline source hashes. They do not run compiler experiments or certify Mosaic.
The validator requires the recorded baseline commit to be available locally.

Full evidence retained locally
The original /data/dev/Mosaic-compatibility-architecture workspace retains the
complete prior compiler-research.tar.gz, downloaded official HTML snapshots,
line-numbered source snapshots and original validation records. These bulky
files are not included in this report-only PR. Their original checksums are
recorded in research-evidence/local-workspace.SHA256SUMS; it describes the local
archive and is not the checksum manifest for this committed package.

Prior experiments remain separately available in
/data/dev/Mosaic-kotlin-compat-research. The committed previous report explains
experiment coverage, failures, limitations and the scripts/logs in that archive.
Official sources can be located through sources.json; repository sources can be
retrieved with git show using the recorded immutable commit and relative path.

No production, test, CI, release configuration or website files are changed.
Full Gradle builds were not repeated for this report-only submission; the root
build includes publication-install tests, and artifact publication remains outside
this task's authorization. Prior build results retain their original limitations.
