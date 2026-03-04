# messenger-slim

Minimal, **local-only** workflow for experimenting with a slimmed Messenger build from your own legally obtained APK.

> ⚠️ This repository does **not** include Facebook/Meta binaries.
> - Do not commit APKs, decompiled sources, keys, or proprietary assets.
> - Use only your own APK copy for local experimentation.

## Target version

- Messenger: `550.0.0.45.63`
- Platform: Windows (PowerShell)

## What this repo contains

- `scripts/prepare-workspace.ps1` – creates local working folders and checks basic tools.
- `scripts/decompile.ps1` – decompiles your local APK with apktool.
- `scripts/rebuild-sign.ps1` – rebuilds + signs an APK (debug key by default).
- `scripts/apply-ai-cuts.ps1` – applies current AI entrypoint cut patch set to local smali.
- `config/features-to-disable.json` – your patch intent checklist (Stories, Reels, AI, etc.).

## Prerequisites (install locally)

- Java 17+
- `apktool` (or place `apktool.bat` / `apktool.jar` into `tools/`)
- Android build-tools (`zipalign`, `apksigner`)

> `zipalign` and `apksigner` are auto-detected from `%LOCALAPPDATA%\Android\Sdk\build-tools\*` if not in PATH.

## Quick start

1. Put your legally obtained Messenger APK in:
   - `input/messenger-550.0.0.45.63.apk`
2. Run:
   - `./scripts/prepare-workspace.ps1`
   - `./scripts/verify-apk.ps1`
   - `./scripts/decompile.ps1`
3. Perform your local code/resource edits inside `work/decompiled`.
4. Rebuild and sign:
   - `./scripts/rebuild-sign.ps1`
5. Output signed APK:
   - `output/messenger-550.0.0.45.63-signed.apk`

## no-res workflow (for problematic APK resources)

If regular resource rebuild fails on this APK source, use `work/decompiled_nres` and apply the patch set there:

- `./scripts/apply-ai-cuts.ps1`

Then rebuild/sign from `work/decompiled_nres` (same local-only policy).

## APK source note

- Do not use random third-party APK websites.
- Use only a legally obtained copy (e.g., from your own device backup/export).

## Safety and legal notes

- Never upload patched APKs to GitHub.
- Keep all modification output local.
- Test on your own device only.

## Suggested next step

Start with low-risk UI hiding and feature entry-point disabling defined in `config/features-to-disable.json`, then iterate per release.
