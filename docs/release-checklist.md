# ReClip Beta Release Checklist

Use this checklist for each beta cut.

## 1) Versioning

- Update `app/build.gradle.kts`:
  - Bump `versionCode` by +1.
  - Set `versionName` to next beta (example: `1.1.0-beta.2`).
- Choose matching git tag (example: `v1.1.0-beta.2`).

## 2) README governance

- Update `README.md` for any material feature/UX/behavior changes:
  - feature/support matrix,
  - behavior notes (Desktop Mode, routing, history sync, etc.),
  - build/release notes when changed.
- Confirm README governance check passes in CI.

## 3) Local validation

- Run:
  - `.\gradlew.bat :app:assembleDebug`
  - `.\gradlew.bat :app:assembleRelease`
- (Optional device sanity):
  - `adb install -r app\build\outputs\apk\debug\app-debug.apk`

### Smoke validation matrix (required)

| Flow | Expected result | Pass/Fail |
|---|---|---|
| Mobile-only download flow | Fetch, download, and open/share work from phone UI |  |
| Desktop -> Phone destination | Desktop-triggered save appears in phone Downloads + history |  |
| Desktop -> Computer destination | Desktop receives file handoff link/download on completion |  |
| History sync verification | Mobile and desktop history views converge within one poll interval |  |

## 4) Publish pre-release

- Trigger `.github/workflows/beta-release.yml`:
  - via `workflow_dispatch`, or
  - by pushing a matching beta tag (`v*-beta.*`).
- Confirm release includes:
  - `ReClip-beta-debug.apk`
  - `ReClip-beta-release-unsigned.apk`
- Confirm notes include:
  - highlights,
  - breaking/behavior changes,
  - known issues,
  - install instructions,
  - commit range.
