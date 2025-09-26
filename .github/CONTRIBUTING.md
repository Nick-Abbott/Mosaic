# Contributing to Mosaic

Thanks for your interest in contributing! 🎉

## How to Contribute
- **Issues**: Use GitHub Issues for bug reports or feature requests. Please include steps to reproduce bugs.
- **Pull Requests**: Fork the repo, create a feature branch, and submit a PR against `main`. Link PRs to an issue when possible.
- **Code Style**: Styling is enforced by ktlint. Use `./gradlew ktlintFormat` to auto-fix most issues. Keep commits small and descriptive.
- **Tests**: PRs must meet coverage requirements enforced by Kover. Add or update tests for any new functionality.
- **Commit Messages**: Use clear, imperative titles (e.g., "Add X" not "Added X").

## Development Setup
1. Clone the repo and open in IntelliJ IDEA.
2. Build with Gradle: `./gradlew build`
3. Run tests: `./gradlew test`
4. Use `./gradlew -p examples` to run commands on projects within the `examples/` folder.

## Communication
- For general Q&A, use GitHub Issues.
- For bigger ideas or architectural discussions, join us in [GitHub Discussions](../discussions).