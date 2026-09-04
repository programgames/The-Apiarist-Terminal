# Summary
Explain the motivation and context for this change.

## Type of change
- [ ] Bug fix (non-breaking change that fixes an issue)
- [ ] New feature (non-breaking change that adds functionality)
- [ ] Breaking change (fix or feature that would cause existing functionality to not work as expected)
- [ ] Chore/Refactor (no functional changes)
- [ ] Documentation update only

## Related issues
Closes #

## What changed

-

## How to test
Provide steps and any relevant commands.

```bash
# From repo root
./gradlew setupDecompWorkspace
./gradlew build
```

If this adds/changes runtime behavior, include a minimal OC program or steps to verify in-game.

## Checklist
- [ ] I built the project locally with Java 8 using `./gradlew build` and ran `./gradlew test`.
- [ ] I updated documentation (README/DEVELOPMENT.md) as needed.
- [ ] I followed the existing code style.
- [ ] I did not include secrets or private data.
- [ ] If adding a new driver, I registered it in `DriverRegistry` and documented its callbacks in `docs/components/<component>.md`.
- [ ] If touching `mcmod.info`, it remains valid and accurate.
- [ ] I searched for existing PRs/issues that cover this change.

## Additional context
Add any other context that reviewers should know.
