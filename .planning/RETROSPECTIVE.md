# Retrospective

Living record of what shipped, what worked, and what did not. Appended at each milestone close.

---

## Milestone: v1.3 — 引导式配置与友好错误反馈

**Shipped:** 2026-11-17
**Phases:** 4 | **Plans:** 41

### What Was Built

A single error "translate, don't display" layer replacing 87 scattered `ElMessage.error` call sites (4 client-side pre-checks remain, by design); a metadata-driven threshold wizard with recommended values, per-field explanations, a four-zone preview bar and a natural-language confirmation sentence; and a non-blocking health-profile guidance system — completeness score, per-field rationale, impact banner, estimate badges — with every substituted default disclosed by the backend instead of silently applied.

Underneath it: the inverted `ThresholdRangeChecker` warning-band condition is fixed, `classify()` is now the one place four-band semantics live, metric keys are canonicalized on both write paths and every read uses alias-set IN, and a business-failure envelope finally carries its semantic HTTP status rather than a constant 500.

### What Worked

- **Deriving truths from evidence, then testing them.** The 14-05 / 14-09 / 14-13 executors ran mutation tests (7, 6, and 7 mutations) to prove their assertions were non-vacuous, and rejected tests that passed against deliberate defects. That discipline is what made this milestone's audit findings actionable rather than arguable.
- **Recording environment false positives explicitly.** Two separate investigations (Phase 12's wizard auto-close, Phase 14's `:focus-within` halo) traced to the same root cause: `document.hasFocus() === false` freezes the CSS animation timeline. Documenting them once kept the second from costing the same hours as the first.
- **The milestone audit earning its cost.** It found three real defects that every phase-level verification had passed over — including two silent-error regressions that the very commit meant to improve error reporting had introduced.

### What Was Inefficient

- **Fingerprints kept going stale for a reason worth fixing upstream.** Phases 11 and 13 went `stale` twice: first because Phase 14's migration touched their `covered_files`, then because a later commit changed a contract they had recorded as fixed. The second time was *correct* behaviour — but the first was noise. A phase's `covered_files` should probably exclude files that a later phase legitimately owns.
- **UAT passed N-1 by doing the extra click.** The profile create-mode guard could never match the real wire shape, and the UAT step passed because it clicked 编辑 first and recorded "编辑按钮可用". Passing a step by performing an undocumented extra action is a gap in the step, not a pass.
- **`14-VERIFICATION.md` asserted unreachability without checking.** It claimed four `presentStates` omissions were unreachable because "controllers are `@PreAuthorize("isAuthenticated()")`, service throws 404/400, no `AUTH_FORBIDDEN`". `UserThresholdServiceImpl:94` throws `AUTH_FORBIDDEN` directly. The claim was not verified, and it licensed four silent sites.

### Patterns Established

- **Classification order as an explicit contract.** `appError.ts` documents a 9-step order where each step's position relative to its neighbours is load-bearing, and the comment explains what breaks if you move it. Tests pin the order rather than the individual branches.
- **Three evidence forms, never one.** 403/404 accept numeric envelope code, real HTTP status, *and* `errorCode` regex. Keeping the regex is not redundancy — it is old-backend compatibility, and the comment says so, so nobody deletes it as dead.
- **`presentStates` as the "no state block here" opt-in.** The default is zero-toast for state-kind failures because a state block should own the presentation. A page with no block must say so explicitly. This is a good shape: the safe default is silent-but-correct, and the exception is one visible flag.
- **Mutation testing as the acceptance bar for a regression test.** Every fix in this milestone's audit close was accompanied by a deliberate break that turned exactly one test red.

### Key Lessons

1. **A commit that only "improves reporting" can still change behaviour.** `ee4d534` was described as making envelope codes honest. It also flipped nine error codes from `business` (visible toast) to `notFound` (silent by default). Nothing in the commit message or the phase verification connected those two facts. When a change alters a classification input, the blast radius is every call site that relied on the old classification — and that set is not obvious from the diff.
2. **"Unreachable" is a claim that needs evidence, not a convenience.** Two audits in this milestone disagreed about it; the one that actually traced the service layer was right.
3. **A test that constructs its own input can test fiction.** `ProfileErrorSample` case B used `status: 404`; the real endpoint answers `status: 200` with an envelope. The test passed for 10 days against a shape that never occurred. Case B2, which uses the real shape, fails when the old guard is restored — and the old case B still passes, which is exactly how a blind spot looks.
4. **Fixing the error channel makes the absence of error handling visible.** Half this milestone's audit findings were pre-existing silent paths that only became *detectable* once the envelope told the truth.

### Cost Observations

- Sessions: 3 (Phase 14 verification/UAT, milestone close, audit)
- Notable: the audit's integration pass was the most productive single step — it converted "4 phases passed" into three specific, reproducible defects with file:line evidence, in one subagent run.
