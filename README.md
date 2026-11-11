# DEX

An agentic Android IDE with an embedded browser and a real Linux (proot) terminal.
Built for low-end devices (minSdk 24), designed to look like nothing else on the shelf.

## Design language

Charred obsidian surfaces, hairline rules, molten ember accents, monospace everywhere.
Three skins share one semantic token set (`?attr/dex*`):

| skin  | character |
|-------|-----------|
| EMBER | default: void black, ember orange, magma pink |
| ACID  | pure black, acid lime + cyan, maximum contrast |
| PAPER | warm fibre, ink text, oxblood accent |

The chrome is a left **rail** (FL / ED / WB / TM / AI) with a top "deck" showing context and
agent state, instead of stock Material tabs and toolbars. File types are shown as tinted
monogram chips, not emoji.

## Panes

- **ED** editor: line numbers, syntax highlighting for ~30 languages, dirty marker, save.
- **WB** browser: WebView with a real address bar, back/forward/reload/home, JS console
  capture, loads local servers and `file://` pages.
- **TM** terminal: proot-hosted Alpine Linux shell — see below.
- **CL** cloud: SSH workspace profiles with a real remote pty — see below.
- **AI** agent: right-hand panel, resizable against the workspace.
- **FL** files: collapsible column with filter, open folder, new file, recents.

## Linux terminal (proot)

DEX provisions a real Linux userland inside the app:

- `libproot.so` ships in the APK (aarch64 + x86_64). Android will not execute binaries
  written to app storage on newer releases, but the native library directory is executable,
  so proot lives there.
- On first use, an **Alpine minirootfs** is downloaded from the official Alpine CDN and
  extracted in-app by a pure-Kotlin tar.gz reader (symlinks, hardlinks, modes preserved).
- The shell runs `alpine /bin/sh` under proot with `/dev`, `/proc`, `/sys` and `/system`
  bound, `-0` fake root, and `--link2symlink`.
- `apk add`, `git`, `gcc`, `python3`, `nodejs` all work: `apk add python3 git`.
- Device storage is reachable at `~/sdcard`.
- The pane has a mobile key row (ESC, TAB, CTRL, ^C, arrows, |, /, -, ~), command history and
  path completion.

**Limitation, stated plainly:** Android exposes no pty to apps. The session is
line-oriented over pipes, so full-screen curses programs (vim, top, htop) cannot draw.
Everything line-based — package managers, compilers, REPLs, git — works.

`targetSdk` is pinned to 28 for this reason: Android only permits executing the rootfs
binaries from app storage for apps targeting SDK 28 or lower.

Third-party binaries: proot (GPL-2.0) and libtalloc from Termux packages. See
`third_party/proot/NOTICE.md`.

## Cloud mode (SSH workspaces)

Free compute, driven from the phone: the **CL** pane connects to any SSH host — a GitHub
Codespace (via `gh`), a Gitpod workspace, a free VM, a home server — and runs a real
**pty** there, so `vim`, `top` and `htop` work remotely even though the local Alpine shell
is line-oriented.

- Profiles (host, port, user, password **or** private key + passphrase) are stored locally;
  add, save and delete from the pane.
- Connecting opens a remote shell rendered by the same terminal engine, with ESC/TAB/CTRL/^C,
  arrows and pty resize on rotation.
- While connected, the agent gains `remote_run`: real commands on the remote filesystem
  (`cat -n`, `sed -i`, heredocs, `git`, `npm`) — the local editor keeps editing device files.

Codespaces: `TM` → `apk add github-cli` → `gh auth login`, then `gh codespace ssh -c NAME`.
Gitpod: add your public key and use `ssh <workspace>@ssh.gitpod.io`. Host keys are accepted on
first use (this is a developer tool); use password auth or a key you control.

The engine is JVM-testable: `app/src/test/.../CloudSessionTest.kt` starts an embedded SSH
server and asserts password auth, pty shell I/O and exec-channel output.

## Agent brain (everything-claude-code)

The agent's playbook is the vendored [ECC](https://github.com/affaan-m/everything-claude-code)
library (MIT), shipped as assets: **71 specialist agents, 66 rules and 314 skills**.
It is loaded on demand, not pasted into every prompt:

- every run gets a compact operating digest (`assets/brain/core.md`) — test-driven, security-first,
  verify-don't-assume, smallest correct diff, close the loop;
- `skills {query}` searches the index (name + description), `skill {name}` loads one playbook,
  e.g. `tdd-workflow`, `verification-loop`, `security-review`, `api-design`, `kotlin/coding-style`;
- `delegate {agent, task}` runs a specialist (`planner`, `code-reviewer`, `security-reviewer`,
  `build-error-resolver`, `architect`…) in a fresh context and returns its answer to the lead agent,
  which still owns every edit and verification.

Only markdown guidance is used — ECC hooks, slash commands and MCP servers have no runtime here.
Attribution and refresh steps: `third_party/everything-claude-code/NOTICE.md`.

## Agent

Multi-step tool loop with a configurable step budget (Settings → agent tool steps):

`list_dir`, `read_file`, `write_file`, `edit_file`, `search`, `run` (shell/python),
`fetch_url`, `open_file`, `apply_to_editor`, `browser_open`, `browser_console`,
`skills`, `skill`, `delegate`, and `remote_run` while a cloud workspace is connected.

Tools are JSON inside `<tool>` tags, executed in order, with results fed back to the model.
Live tool activity appears in the transcript; STOP cancels the run mid-stream.

Providers: OpenAI, Anthropic, Gemini, or any OpenAI-compatible endpoint (Ollama, LM Studio,
Groq, OpenRouter, Together). Keys and model presets are saved as profiles.

## Build

```bash
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk` — requires JDK 17 and Android SDK 34
(`local.properties` → `sdk.dir`).

## First run

1. Settings → provider preset, API key, model → SAVE.
2. `FL` → open a folder, tap a file.
3. `TM` → **PROVISION LINUX** (downloads Alpine once).
4. Ask the agent for a page and watch it open in `WB`.
