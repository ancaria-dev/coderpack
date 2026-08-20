# Style

Short files, one responsibility per type, short names, short packages.
Without fanaticism — clarity wins over brevity when they disagree.

## Naming

The package already carries the category, so the type name does not repeat it:
`event.Damage`, not `event.EntityDamageEvent`. One short segment under the
root — `dev.ancaria.coderpack.api.event`, never `sal.api.events.entity.damage`.

Same in Rust: `router.rs`, `pipe.rs`, `job.rs`. One file per job, no nested
`mod.rs` trees.

## Size

If a file needs a table of contents, split it. A type that needs "and" to be
described is two types.

## Addresses

Never write a game address in Rust, TypeScript or Java by hand. Every address
lives in `mappings/mappings.txt` and reaches code through `tools/addr.py`.
A wrong row there is worse than a missing one, so it carries VA, RVA, calling
convention and a `CONF` column.

## Comments

Explain why, not what. The hook sites in this project mostly need one line
saying which register holds what and why the site was chosen over a
neighbouring one — that context is not recoverable from the code.
