# Release procedure

Keep `mosaic.version` in the root `gradle.properties` as the version source. The
runtime BOM contains `mosaic-core` and `mosaic-test`; analysis artifacts stay
outside it. The changelog entry must have a date before tagging. Release tags
are immutable and must already point to the validated source commit.

Run the manual **Release** workflow from `main` with the existing version tag:

```bash
gh workflow run release.yml --ref main -f tag=0.4.0
```

Configure a protected GitHub Environment named `release` before running it.
Provide these environment secrets there: `MAVEN_CENTRAL_USERNAME`,
`MAVEN_CENTRAL_PASSWORD`, `SIGNING_PRIVATE_KEY`, `SIGNING_PASSWORD`,
`GRADLE_PUBLISH_KEY`, and `GRADLE_PUBLISH_SECRET`. Do not put credentials in
repository files.

The single release job validates the exact tag, runs the repository and example
checks, then signs and automatically releases all six Maven Central modules with
Vanniktech Maven Publish. It then submits `org.buildmosaic.analysis` through
`publishPlugins` from the same workspace, without waiting for Central public
visibility. If a later step fails after Maven publication succeeds, do not
rerun the whole workflow against already published coordinates.

After Plugin Portal submission, the workflow creates a **draft** GitHub Release
from the checked-in release-notes companion and dated changelog entry. The first
Plugin Portal publication may need manual review. Confirm the plugin version and
marker are publicly available, then publish the draft GitHub Release manually.
The workflow never creates or moves a tag.
