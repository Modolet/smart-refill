<!-- @file CHANGELOG.md @brief User-facing release notes. @author XiangX (Modolet) @date 2026-10-19 -->
# Changelog

## 0.1.2 — 2026-10-19

- Support ordinary and advanced Tom wireless terminals carried in valid top-level Accessories functional slots, using live Accessories API reads and validation.
- Keep Tom's original binding, line-of-sight, range, dimension, beacon, loaded-chunk, and extraction checks. Do not patch its permission methods or force-load chunks.
- Select sources deterministically: normal inventory, offhand, then Accessories slot name/index; never roam networks after a shortage.
- Add an independent `accessoryWirelessRefill` switch with English and Simplified Chinese labels/tooltips, and optional terminal entries in the Accessories belt tag. Cosmetic/nested slots remain excluded.
- Add a dedicated real-game accessory-terminal regression suite covering successful extraction, permission failures, manual changes, source order, and conservation.

## 0.1.1 — 2026-10-19

- Publish under the independent **Smart Refill** name, with a new original icon and MIT source repository.
- Add English and Simplified Chinese configuration labels, tooltips, translated priority choices, mod descriptions, and a client-only NeoForge configuration screen.
- Add explicit optional Accessories, Tom's Simple Storage, and ClientSort dependency relationships; keep legacy `stackrefill` incompatible.
- Provide English documentation, source/issues links, provenance, tested versions, and outstanding acceptance checks.
- Keep genuine-consumption refill, manual-edit cancellation, original-slot accessory totems, and transactional wireless extraction from the tested 0.1.0 implementation.

At 0.1.1, accessory-carried terminals were unsupported. Ordinary wireless terminals require line of sight. Totems refill at the player's post tick, leaving a damage window. See [verification boundaries](TEST-RESULTS.md).

## 0.1.0 — local baseline

Initial independent implementation and isolated BMC5 integrated-server regression suite. Not published as a GitHub release.
