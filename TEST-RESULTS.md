<!-- @file TEST-RESULTS.md @brief Runtime evidence and acceptance boundaries. @author XiangX (Modolet) @date 2026-10-19 -->
# Verification record

Date: 2026-10-19 (Asia/Shanghai). The real instance was not modified, its saves were not opened, and the user's running game was not stopped. All runtime tests use disposable clients and new worlds created for this project, without Numen.

## Release 0.1.2

- Compilation, formatting, and static checks: passed in the project Nix environment.
- Installer/rollback: all three temporary-directory fixture tests passed.
- Package contract: passed; matching 25-entry English/Chinese resources, optional terminal tag entries, correct dependency relations, only project-owned classes/resources/license, no nested or third-party JARs.
- Actual BMC5 integrated-server regression: **79 passed, 0 failed**, including the previous 51 checks and **28 dedicated accessory-terminal checks**.
- Actual minimal runtime without any optional integration: **passed** (`OPTIONAL_ABSENCE PASS`), including block refill and localized client configuration.
- Actual Tom-only runtime without Accessories/Curios/compatibility layer: **passed** (`OPTIONAL_ABSENCE PASS`). Genuine consumption calls the wireless fallback after the inventory supplies only 8 of the requested stack, safely loading TomBridge with Accessories absent. Both locales and the configuration extension also pass. All three test clients exited normally; their production JAR hashes match the release artifact.
- Human keyboard/mouse and visual UI acceptance, dedicated multiplayer, positive beacon access, and real-save acceptance: **not performed**. Outstanding limitations below still apply.

JAR: `smartrefill-neoforge-1.21.1-0.1.2.jar`.
SHA-256: `19b145ad17ed5f9e20f018b19c6e7c2168418e2fb4def80fb1402863031afd73`.

The dedicated suite uses actual valid Accessories slots and real Tom networks with the storage GUI closed. Advanced access succeeds 16 blocks from the terminal, beyond the default six-block interaction distance: native Tom extra-slot recognition is exercised. Ordinary access first fails when looking away, then succeeds with the original line-of-sight path. Extraction moves the actual warehouse quantity into the player and leaves the worn terminal's count, components, and stack identity unchanged.

| Dedicated accessory-terminal coverage | Result |
|---|---|
| Valid functional slot, extended native range, advanced extraction and unchanged terminal | Passed |
| Ordinary terminal requires line of sight, native access, real extraction | Passed |
| Unbound, out of range, cross-dimension without beacon, unloaded destination | Rejected without stock loss or forced loading |
| Cosmetic slot and invalid functional slot | Ignored |
| Terminal removed between consumption and flush | No extraction |
| Target changed before flush | Existing target preserved, no extraction |
| Accessory-source switch and wireless master switch | Each prevents extraction |
| Normal inventory and offhand terminal priorities over worn terminal | Deterministic network choice and exact source deduction |
| Partial stock with inventory-first and wireless-first; inventory-only mode | Counts conserved and source order respected |
| Multiple functional-slot terminals and shortage in first network | No network roaming; swapping slot indexes changes the selected network deterministically |
| Actual extraction followed by target-change rollback | Extracted 3 returned; warehouse total restored to 7, existing dirt preserved |

The multiple-terminal fixture temporarily adds one belt slot through Accessories' transient modifier API **only in the disposable test world** and removes it afterward. Production code adds no slots. The complete suite is in `AccessoryTerminalProbes.java`; the delivery bundle extracts its 28 passing checks into `accessory-terminals.log`. Baseline lifecycle, manual-input packet, ClientSort, component matching, and totem tests also passed on this same production JAR.


The current installed Tom 2.4.2 permission path is `StorageTerminalBlockEntity.canInteractWith` → `PlayerInvUtil.findItem` → `Platform.checkExtraSlots` → the existing Curios/Accessories compatibility view. That view reads functional Accessories stacks. New source discovery itself uses AccessoriesCapability, AccessoriesContainer, SlotReference, and AccessoriesAPI.canInsertIntoSlot directly. No Curios API is called by Smart Refill and no Tom permission method is patched. Range is still determined by Tom's first matching carried terminal, not the largest available range. The binding/line-of-sight and loaded-block checks in Smart Refill remain necessary because Tom's permission method alone does not validate a particular terminal's binding.

## Release 0.1.1

- Compilation, formatting, and static checks: passed in the project Nix environment.
- Installer/rollback: three temporary-directory tests passed.
- Package contract: passed; both locales contain the same 23 keys, all three integrations are optional, legacy StackRefill is incompatible, and every JAR entry belongs to this project. No nested or third-party JARs are included.
- Actual BMC5 full-pack integrated-server probes: **51 passed, 0 failed**, including all 50 baseline scenarios and a new client-resource/configuration-extension check. The latter loads English and Simplified Chinese through Minecraft's actual ClientLanguage, checks every configuration label/tooltip and priority enum translation, and creates the registered NeoForge ConfigurationScreen.
- Optional-dependency absence: **passed** in the actual minimal NeoForge client without external mods. Refill, both language resources, translated enums, and configuration-screen extension were exercised; `OPTIONAL_ABSENCE PASS`. Both test clients exited normally. Their installed production JAR hashes match the release JAR.
- Human keyboard/mouse, visual UI layout, dedicated multiplayer, and real-save acceptance: **not performed**.

JAR: `smartrefill-neoforge-1.21.1-0.1.1.jar`.
SHA-256: `d49a6fe14f02334b526a06bd93b2b9cfee13ae34997b9e400317720dfcf55c3e`.

This release adds independent branding, English publication materials, English/Simplified Chinese translations, a client-only configuration screen, and explicit optional dependency relationships. The production refill algorithm is unchanged from the 0.1.0 baseline.

## Baseline 0.1.0

- Compilation: passed using Java 21, the Nix project environment, and actual installed NeoForge 21.1.250 libraries. Two independent builds produced identical hashes.
- Formatting and static checks: google-java-format, ruff check, and ruff format passed.
- Installer/rollback: three temporary-directory fixture tests passed. A read-only preview against the real instance detected its running Java session and refused replacement.
- Actual full-pack integrated-server automated probes: **50 passed, 0 failed** in a newly created SmartRefill-CI world. These are real runtime method/protocol probes, not pure mocks or human mouse/keyboard acceptance.
- Optional-dependency absence: actual minimal client without Accessories, Accessorify, Curios, Tom, or ClientSort successfully refilled a consumed block; `OPTIONAL_ABSENCE PASS`.
- Human input, dedicated multiplayer, and real-save acceptance: **not performed**.

Baseline JAR: `smartrefill-neoforge-1.21.1-0.1.0.jar`.
SHA-256: `5e239f98066a1f1689d142217582f5f171c8ec4e29de2091220ff0e03eb932bb`.

## Test environment

Better MC BMC5 v53, Minecraft 1.21.1, NeoForge 21.1.250; Accessories 1.1.0-beta.53+1.21.1, Accessorify 2.4.0-beta.5, Accessories compatibility layer 0.1.12, Curios 9.5.1, Tom's Simple Storage 2.4.2, ClientSort 3.89.0, MouseTweaks, and the pack's accessory fixes.

The full-pack copy retained 360 of 363 original external JARs, excluding legacy StackRefill, Numen, and CrashAssistant, and added only Smart Refill plus the disposable test plugin. Nested dependencies loaded normally. Private Xvfb :142, software rendering, 4 GiB heap, offline test identity. Inventory creation, teleports, test blocks, and keepInventory apply only to the new test world. Test plugins are excluded from production JARs.

## Passed runtime scenarios

| Scenario | Evidence and limit |
|---|---|
| Last block placement and refill | Actual ServerPlayerGameMode.useItemOn, world placement, exhausted stack, source deduction, and target readback |
| Last apple and snowball | Real finishUsingItem/use paths; food completion is invoked directly rather than holding a physical key |
| Iron pickaxe and helmet break | Actual hurtAndBreak, replacement deduction; damage may differ |
| Mouse, Shift, number swap, F, Q, throw, double-click, drag entry | Actual server container/action packet handlers; not physical input; drag entry only |
| Same-tick click/write/selection cancels pending refill | Genuine consumption followed by an operation before flush; old and new slots read back |
| ClientSort sort cancels pending refill | Actual SortPayload and SortHandler server acceleration path |
| Mismatched components rejected | Same item ID with a different custom name is not taken; full comparison uses isSameItemSameComponents |
| Two accessory totem activations | Actual Accessorify FAKE_HAND returns the charm's real stack; lethal hurt leaves health 1, original slot exhausted, then refilled exactly once |
| Manual accessory removal and pending-target edit | Accessories SlotReference writes/readback; no replacement after manual removal |
| Ordinary line-of-sight and advanced bound wireless access, GUI closed | Actual Tom connector/chest network; warehouse 64→0/player 0→64; ordinary terminal 7→0/player 0→7 |
| Partial inventory plus warehouse | Inventory reserve 3 and network 10 produce target 13, both sources exhausted |
| Three source priority settings | Actual source deduction order; INVENTORY_ONLY does not use the network |
| Deterministic network selection | First accessible network out of stock leaves second network's 9 untouched; changing terminal slot order takes 2 from second network, then returns them |
| Unbound, out of range, no stock, unloaded destination, missing terminal, cross-dimension without required beacon | Failure without warehouse deduction; unloaded target is not force-loaded |
| Target changes or rejects insertion after extraction | Failure-injection fixtures invoke production transaction delivery; extracted 3/2 returned, total remains 7; existing dirt is not overwritten |
| Dimension change | Actual Nether round trip after consumption cancels old pending event |
| Death and respawn | Pack loading protection required direct health=0 then actual ServerPlayer.die; actual PERFORM_RESPAWN handler; keepInventory fixture preserves 8 stones |
| Disconnect, close, reopen same isolated world | Actual disconnect and world reopen preserve total inventory stone count 8 |

`actual_disconnect_no_pending_transfer` checks item conservation, not immediate synchronous TCP teardown. A pending refill may complete before asynchronous disconnect finishes.

The consecutive totem test explicitly flushes between two lethal hits. It proves a replenished totem can trigger again, not survival against another lethal hit before the player's post-tick refill. The damage window is intentional and documented.

## Outstanding checks and unsupported behavior

- Dedicated server, concurrent clients, latency, packet loss, and actual server/client mod-installation combinations.
- Physical mouse/keyboard sequences, full drag distribution, complete sorted UI layout, and visual Accessories panel synchronization.
- Unloading and reloading an actively working network. The tested unloaded-target case proves refusal without forced loading.
- Positive infinite-range/cross-dimension beacon cases. Negative cases passed; production reuses Tom's canInteractWith(false).
- Full in-game `/reload` and configuration hot-reload lifecycle. No previous-tick disappearance heuristic or persisted pending queue exists, but that design fact is not runtime acceptance.
- Remote tool/enchanted-tool replacement, all filter combinations, and network return failure combined with a full player inventory and dropped-entity fallback.
- Exhaustive potion/enchantment/container-component combinations. Custom-name mismatch was tested and the official complete component comparison is used.
- Nested/cosmetic terminal sources remain unsupported. Occupied bowl/bottle targets and non-vanilla totems are not replaced. Functional-slot terminal support is covered separately in 0.1.2.

## Development failures and corrections

Earlier rounds retained failed logs for incorrect Shift count expectations, test-player falls, LoadingProtection cancelling damage, terminal/player head orientation, Tom same-tick tracker visibility, and asynchronous disconnect timing. Production fixes refresh a cold terminal through Tom's public path and cancel pending work for ClientSort's custom requests. The final baseline removed diagnostic reflection and pre-refreshing; production code itself performed successful extraction.

Original consoles, disposable worlds, and previous rounds remain in local ignored `build/`. Delivery bundles include compact final logs and a development-round summary. Failure logs were not rewritten as passing evidence.
