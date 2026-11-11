# everything-claude-code (ECC) — vendored brain pack

DEX ships a curated copy of the ECC playbook as Android assets under
`app/src/main/assets/brain/`. The agent loads entries on demand through the
`skills` / `skill` tools and delegates to agent personas with `delegate`.

Source: https://github.com/affaan-m/everything-claude-code
License: MIT (see `app/src/main/assets/brain/LICENSE`)
Copyright (c) 2026 Affaan Mustafa

## What is vendored

| kind  | count | source path                                  |
|-------|-------|----------------------------------------------|
| agent | 71    | `agents/*.md`                                 |
| rule  | 66    | `rules/{common,kotlin,java,python,web,react,react-native,typescript,rust,golang,cpp}/*.md` |
| skill | 314   | `skills/*/SKILL.md` (entry point only)        |

Plus `AGENTS.md`, `SOUL.md` and the upstream `LICENSE`. `brain/index.tsv` is
generated at vendor time (name, description, asset path) and is the only file
DEX reads eagerly; `brain/core.md` is an authored operating digest that is not
part of the upstream repository.

## Refreshing

```bash
curl -fsSL -o ecc.tgz https://codeload.github.com/affaan-m/everything-claude-code/tar.gz/refs/heads/main
tar xzf ecc.tgz   # ECC-main/
# copy agents, selected rules and each skills/*/SKILL.md into app/src/main/assets/brain/
# regenerate index.tsv (see git history for the extraction script)
```

Skills that depend on hooks, MCP servers or Claude Code slash commands cannot
run inside DEX; only their markdown guidance is used.
