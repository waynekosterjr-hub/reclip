# CLAUDE.md — ReClip workflow

## Local repo & sync workflow

- **Canonical local repo (developer's machine):** `C:\Users\micry\GIT\reclip` (Windows).
- **Sync tool:** GitHub Desktop — the developer pulls/pushes between the Windows
  folder and the GitHub remote (`waynekosterjr-hub/reclip`).
- **Agent sandbox copy:** Claude works in a separate Linux checkout and never
  touches the Windows path directly. **GitHub is the shared sync point.**
  - Flow: Claude commits → pushes to remote → developer's GitHub Desktop pulls
    into `C:\Users\micry\GIT\reclip` (and vice versa).
  - Before starting work, pull the latest so the sandbox isn't stale.

## Target branch

- **Push all work to `Android-Release`** — this is the active dev/release line
  (ahead of `Codex`; carries the `v1.1.0-beta.*` tags and Desktop Mode work).
- Do not push to other branches without explicit permission.

## Notes

- See `AGENTS.md` (if present on the branch) for build/architecture details:
  WebView UI → Java bridge → Chaquopy Python (`reclip_engine.py`) → FFmpeg.
- Never commit secrets (RevenueCat keys, OpenAI keys, signing material).
