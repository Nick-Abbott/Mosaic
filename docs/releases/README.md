# Release procedure

Keep `mosaic.version` in the root `gradle.properties` as the version source. The
runtime BOM contains `mosaic-core` and `mosaic-test`; analysis artifacts stay
outside it. The changelog entry must have a date before tagging. Release tags
are immutable and must already point to the validated source commit.

Run the manual **Release** workflow from `main` with the existing version tag:

```bash
gh workflow run release.yml --ref main -f tag=0.3.0
```

Configure a protected GitHub Environment named `release` before running it.
Provide these environment secrets there: `MAVEN_CENTRAL_USERNAME`,
`MAVEN_CENTRAL_PASSWORD`, `SIGNING_PRIVATE_KEY`, `SIGNING_PASSWORD`,
`GRADLE_PUBLISH_KEY`, and `GRADLE_PUBLISH_SECRET`. Do not put credentials in
repository files.

The workflow validates the exact tag without release secrets, then uses that
commit to sign and automatically release all six Maven Central modules with
Vanniktech Maven Publish. Once the required implementation artifacts are public,
it submits `org.buildmosaic.analysis` through `publishPlugins`. If Central has
not propagated yet, the Plugin Portal job fails before uploading; rerun only
failed jobs later, without rerunning the successful Maven job.

After Plugin Portal submission, the workflow creates a **draft** GitHub Release
from the checked-in release-notes companion and dated changelog entry. The first
Plugin Portal publication may need manual review. Confirm the plugin version and
marker are publicly available, then publish the draft GitHub Release manually.
The workflow never creates or moves a tag.
