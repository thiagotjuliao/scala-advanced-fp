# Working in this repository

<!-- What this project is, in one or two sentences, and what a change to it is
     for. Replace this comment. -->

How code and prose are written here — shared with every project built from
project-templates. Where it and this file disagree, this file wins.

@CONVENTIONS.md

## Commands

| command | |
| --- | --- |
| `sbt verify` | the gate: `scalafmtCheckAll`, `scalafmtSbtCheck`, `testFull` — what CI runs |
| `sbt scalafmtAll` | apply the formatting contract |

`testFull`, not `test`: under sbt 2 `test` is incremental and its greenest
possible run is the one that ran nothing.

## Conventions

- Scala 3 indentation syntax, formatted by `.scalafmt.conf`. Run `sbt scalafmtAll`.
- English everywhere: code, comments, documents, commit messages.
- Scripts come in pairs, `.sh` and `.ps1`, and do the same thing.
