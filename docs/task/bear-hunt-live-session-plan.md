# Bear Hunt live-session plan

Status: the critical replacement path was implemented on PR #346 on 23 September 2026: verified local navigation, a retained Bear anchor, explicit five-minute selection, distinct page-readiness handling, and three-scan final drain. Deterministic tests pass; the PR stays draft until today's live Bear acceptance passes.

## Goal

Run the full 30-minute Bear Hunt as one fast, state-aware session:

- start up to five own rallies when the timing permits;
- continuously fill every configured joining march in the user's configured order;
- reuse a formation as soon as its march returns;
- keep trying after full, departed, or stale rallies;
- stop creating own rallies at `event end - 5:30`, while continuing to join rallies already in progress;
- finish cleanly at the event end without disturbing unrelated tasks.

The active Bear loop must not contain blind fixed sleeps. Screen capture and verified state changes are the pacing mechanism. Longer waits for a march return must yield to a scheduled deadline and remain cancellable; they must not hold the profile in a sleeping navigation routine.

The implemented runtime order is:

1. Prepare once: disable conflicting auto-join, perform configured recall/pet work, locate Bear from the real `GAME_HOME_WORLD` root anchor, and retain the World-at-Bear anchor.
2. Read all march rows from one frame and reserve the tracked own-rally row.
3. Before T-5:30, launch an own rally only when its tracked row is visibly idle.
   Explicitly select five minutes and require the green tick before continuing.
   Until that own rally is confirmed, replay Alliance → Territory → Special Buildings → configured Go → Rally; a recoverable failure must not skip ahead to joins.
4. Fill every genuinely free join march in configured formation order.
5. Rank visible candidates by capacity margin, remaining countdown, occupancy, then stable row position.
6. Use fresh-frame predicates for every transition. Fixed navigation/deploy sleeps are not allowed in the active loop.
7. Wait only for a known march-return/event-end deadline, and keep that wait cancellable.
8. Recover from the current screen without a blind second Back tap or resetting confirmed formation/rally state.

## Facts confirmed during the live run

1. An own Bear rally has a fixed five-minute preparation timer. The current three-minute assumption is wrong.
2. The minimum observed travel is about seven seconds outbound and seven seconds returning.
3. The safe final-own-rally cutoff is 5:30 before the event end. After this cutoff the session becomes join-only.
4. During the final five minutes, existing rallies may still be joined. The bot must not create another own rally or wait for new rallies to appear.
5. Formation configuration refers to slot position. Users may rename the flags.
6. A selected formation is proven by its yellow slot border. Merely seeing the slot is not proof.
7. The own-rally formation must remain reserved. The configured join formations are attempted in their exact configured order.
8. A rally is joinable only when all of these are true:
   - it targets the active Bear;
   - its join button is green;
   - it has a member slot available;
   - its remaining troop capacity can hold the whole selected formation;
   - it has not departed.
9. Member count alone is insufficient. A rally can show open member slots while its troop capacity is already full.
10. The green button turns grey when the rally can no longer accept the march. The state can change while the deployment screen is opening.
11. Older/top rallies are often already full or about to march. The bot must scan visible rows and prefer a newer rally with ample troop capacity rather than blindly tapping the first row.
12. A failed candidate must not consume or advance the formation. Keep the same formation and immediately try the next candidate.
13. A successful join can be confirmed by the green participation marker/player row and by the selected formation becoming busy.
14. Returned formations must be recycled immediately. The live run successfully reused a returned joining slot.
15. The red rally indicator on the World screen is the fastest entry to the rally list.
16. Repeating Alliance -> Territory -> Special Buildings for every own rally is too slow. The current path also stacks several fixed waits and took close to a minute in practice.

## Live acceptance failure — 19 September 2026

The current Elroy build was definitely running: its new session-state messages appeared in the account log. The full 30-minute event produced:

- 0 confirmed own rallies;
- 0 confirmed rally joins;
- 19 own-rally start attempts;
- 14 reads of the remembered three-minute rally timer;
- 14 failed yellow-selection checks for formation slot 1;
- 38 generic recoveries;
- all six march slots idle for the entire event.

The repeated path was:

`prepare -> lose Bear anchor -> generic recovery -> read idle marches -> Alliance -> Territory -> Special Buildings -> Bear -> rally setup -> observe remembered 3-minute timer -> classify slot 1 as empty or missing -> tap slot 1 -> fail yellow verification -> back out -> generic World recovery -> repeat`

Some attempts spent about 40 seconds merely reaching the rally setup screen. The final own-rally attempt began only seven seconds before the T-5:30 cutoff and failed. After the cutoff, one War-list attempt appears to have been treated as an empty final list; the routine then did nothing until the event ended.

Cleanup and rescheduling at the event end worked. That is the only part of this live acceptance run that passed.

### What the failure proves

1. The current coordinator is a state machine around old scripted navigation calls, not a live frame-driven controller.
2. `readRallySetTimeSeconds(300)` observes the selected option; it does not select five minutes. The game had remembered three minutes.
3. Formation selection depends on fixed slot coordinates and a fixed yellow-border region that did not match the live screen. A single tap followed by a failed colour check abandons the attempt.
4. `bearAnchorKnown` is a boolean memory, not current visual proof. It can be true while the expected Bear control is no longer usable.
5. Recovery always resets toward the generic World screen. It discards useful local state and repeats the longest route.
6. A 1.5-second War transition timeout can mean slow loading, a missed frame, or an actually empty list. Those conditions are currently conflated.
7. One `NO_JOINABLE_RALLY` result in the final window permanently sets `finalJoinListDrained`, after which the session sleeps until the event ends.
8. Join scanning, waiting, and Bear location transitions are deliberately suppressed from normal state logs, making the most important failures hard to diagnose.
9. Own-rally failure can consume the whole useful event window instead of degrading to join-only work.

## Replacement architecture: use Fishing's model

Fishing works because perception, policy, action, and recovery are separate and every action is based on a recent frame. Bear must use the same shape rather than adding more sleeps and retries to the existing routine.

### 1. Latest-frame stream

Introduce a shared `BearFrame` carrying the image, monotonically increasing sequence number, and capture time. All Bear decisions consume the newest frame. An action may only use a frame younger than 250 ms and must verify its result on a later frame.

Do not let navigation helpers, OCR calls, and formation helpers capture unrelated screens independently during one decision. They must operate on the same observation or explicitly request a newer one.

### 2. Explicit screen classifier

Classify every fresh frame as one of:

- `HOME_OR_WORLD`;
- `ALLIANCE_HOME`;
- `ALLIANCE_TERRITORY`;
- `SPECIAL_BUILDINGS`;
- `WORLD_AT_BEAR`;
- `BEAR_RALLY_SETUP`;
- `WAR_LIST`;
- `FORMATION_PICKER`;
- `DEPLOY_CONFIRMATION`;
- `UNKNOWN`.

The classifier must require positive evidence. A missing template is not proof of another screen. Unknown frames preserve the previous confirmed state for a short bounded period but never authorize a blind tap.

### 3. Asynchronous observer

Add a `BearSessionObserver`, following `FishingDepthMonitor`, that consumes the frame stream and maintains:

- current confirmed screen;
- event time remaining;
- march-slot ready/busy state and return countdowns;
- currently selected formation slot;
- visible War-list rally rows and their freshness;
- own-rally preparation timer selection;
- last confirmed action and outcome.

OCR must run outside the immediate input path and reuse OCR sessions. A slow OCR read may enrich the next decision but may not stall a time-sensitive green-button or formation-selection action.

### 4. Pure policy engine

Move scheduling into a `BearDecisionEngine` that receives the latest observation and returns one action. Its priority order is:

1. honour cancellation and event end;
2. reconcile visible marches and release returned formations;
3. after T-5:30, prohibit own-rally actions completely;
4. if a configured join formation is ready and a joinable candidate is visible, join it immediately;
5. before T-5:30, start an own rally only when doing so cannot discard an immediate join opportunity;
6. locally recover the current screen;
7. otherwise wait for the next fresh frame or known march deadline.

An own-rally failure must never block joining. If own setup cannot be verified, record that outcome, leave the setup screen locally, and continue filling join formations.

### 5. Verified action executor

Add a `BearActionExecutor`, following Fishing's verified taps:

- locate the exact control in a current frame;
- reject a stale frame before tapping;
- tap once;
- wait for a newer frame containing the expected postcondition;
- allow at most one second tap only when a fresh frame proves the first tap caused no state change;
- never issue the second tap after cancellation or an interrupted poll.

No action may assume success because a timeout elapsed.

### 6. Local recovery graph

Recovery starts from the classified screen and takes the shortest verified edge:

- War list -> one verified Back -> World;
- formation picker -> one verified Back -> rally setup or War list;
- rally setup -> one verified Back -> World-at-Bear;
- World-at-Bear -> tap the visually confirmed Bear anchor;
- World without a Bear anchor -> Alliance -> Territory -> Special Buildings -> Go;
- unknown -> wait briefly for a fresh classification, then use one Back at a time and reclassify after each transition.

Never perform a multi-Back reset. `bearAnchorKnown` must become visual evidence on the current frame, not a retained boolean.

## Exact own-rally flow

1. Confirm `WORLD_AT_BEAR` from a fresh frame.
2. Tap the Bear and verify the rally control appears.
3. Open rally setup and verify the timer dialog.
4. Explicitly select the five-minute option.
5. Verify the green check is on five minutes. Reading a remembered value is insufficient.
6. Derive the visible formation-bar geometry from the current frame.
7. Tap the configured own slot by position; names are irrelevant.
8. Verify that exact slot has a yellow outline in a newer frame.
9. If a current frame proves no change, permit one corrected tap using the recalculated slot centre.
10. If selection still cannot be proved, leave setup locally and continue join work. Do not restart the full navigation path.
11. Recheck the cutoff using five minutes plus observed outbound travel.
12. Deploy and confirm a new busy march row or own-rally marker before recording success.

The slot verifier needs recorded live frames for unselected, yellow-selected, locked, empty-looking, and busy slots. Hard-coded coordinates alone are not acceptable because the live bar did not match the assumed region.

## Exact join flow

1. From World, use the red War indicator and verify `WAR_LIST`.
2. A missing plus icon during the transition is `PAGE_NOT_READY`, not `NO_JOINABLE_RALLY`.
3. Parse all visible Bear rows from one fresh frame.
4. Reject grey, departed, already joined, wrong-target, member-full, or troop-capacity-full rows.
5. Rank the remaining candidates by capacity margin and time remaining.
6. Keep one pending configured formation until a deployment is confirmed.
7. Tap the candidate, verify the formation picker, select the pending slot, verify yellow, revalidate rally capacity, and deploy.
8. If the rally fills or departs, return to the list and try the next candidate with the same formation.
9. Only advance the configured formation order after confirmed deployment.
10. After a join, return to World with one verified transition so the list can refresh and rows can shift naturally.

`NO_JOINABLE_RALLY` is only valid when `WAR_LIST` is positively confirmed and a complete fresh scan finds no valid row. Navigation timeout, OCR miss, stale frame, and an empty confirmed list are separate outcomes.

During the final window, never permanently drain joining from one scan. Require at least three confirmed empty full-list observations on distinct fresh frames, covering the visible list/required scroll range. Until then, continue bounded rechecks. A missing War control or plus template never counts toward drain confirmation.

## Observability required

Every meaningful transition must be logged with frame sequence and elapsed time:

- classified screen and confidence/evidence;
- requested action and source frame age;
- verified postcondition or precise failure reason;
- formation slot and yellow-pixel/geometry evidence;
- rally timer option before and after selection;
- rally candidate rejection reason;
- march-slot lifecycle;
- local recovery edge;
- join-drain evidence count;
- own-launch cutoff decision.

Do not suppress `FILL_JOIN_SLOTS`, `WAIT_FOR_NEXT_USEFUL_DEADLINE`, or Bear-location transitions. Logging must be rate-limited by state/evidence change, not removed.

## Session states

Replace the loose navigation loop with explicit, observable states:

1. `PREPARE_SESSION`
   - disable conflicting auto-join once;
   - recall troops and activate pets only if configured;
   - locate the configured trap once and retain a verified World-at-Bear anchor.
2. `OWN_RALLY_READY`
   - allowed only before the T-5:30 cutoff;
   - open the Bear from the retained anchor;
   - select the reserved own-rally slot and verify yellow;
   - deploy and verify the new rally/march row.
3. `JOIN_SCAN`
   - open the War list from the red indicator;
   - capture one fresh frame and evaluate every visible Bear row;
   - scroll toward newer rows only when the current frame has no valid candidate.
4. `JOIN_DEPLOY`
   - use the next ready configured join slot;
   - verify that slot is yellow;
   - revalidate the candidate and deploy;
   - advance the formation order only after confirmed success.
5. `WAIT_FOR_RETURN`
   - calculate the earliest useful return deadline from march rows;
   - yield until just before that deadline instead of sleeping inside the task;
   - re-read the march state before acting.
6. `JOIN_ONLY`
   - begins at T-5:30;
   - never calls an own rally;
   - continues joining rallies already present/in progress;
   - in the final five minutes, does not wait for new rally creation after the list drains.
7. `FINISH`
   - stop at the event end;
   - return to a known screen;
   - release Bear protection and resume normal scheduling once.
8. `RECOVER`
   - identify the current screen from a fresh frame;
   - take the shortest transition back to the required state;
   - preserve formation order, confirmed marches, and the own-rally cutoff.

## Remove the slow path

### Fixed waits

Remove the active-session `sleepTask(...)` calls around Bear navigation, rally opening, formation selection, deployment, and confirmation. Replace them with bounded fresh-frame predicates such as:

- World-at-Bear anchor visible;
- War list visible;
- green Bear join button visible;
- deployment screen visible;
- selected formation border yellow;
- Deploy button disappeared and a new busy march/participant marker appeared.

There should be no artificial delay between a successful predicate and the next action. Screen capture latency already provides natural pacing. Polls must check cancellation and use a monotonic timeout.

### Navigation

- Locate the trap once during preparation, not once per own rally.
- Preserve the World camera at the Bear whenever possible.
- Use the red World rally indicator for every join pass.
- After a join, return with exactly one verified transition; never issue a second blind Back tap.
- Use Alliance -> Territory -> Special Buildings only as recovery when the Bear anchor is genuinely lost.
- Recovery must inspect the current screen first instead of resetting through a long generic World-navigation routine.

## Rally selection and formation handling

Build a `BearRallyCandidate` from one frame with:

- row position;
- Bear-target evidence;
- green/grey join-button state;
- current and maximum troop count;
- member count;
- remaining rally countdown;
- whether this profile already joined it.

Reject candidates that are grey, departed, already joined, or lack enough troop capacity for the pending formation. Rank valid candidates by:

1. sufficient troop-capacity margin;
2. longer remaining countdown/newer rally;
3. lower current occupancy;
4. stable on-screen row position.

For every deployment:

1. keep the same pending formation until success;
2. tap its configured slot position;
3. verify the slot turns yellow and contains deployable troops;
4. verify the rally is still joinable;
5. deploy;
6. confirm success from fresh evidence;
7. only then advance to the next configured formation.

If the rally fills, departs, disappears, or turns grey, return to the list and immediately try the next candidate with the same formation.

## Timing rules

- Event duration: 30 minutes.
- Own-rally preparation: 5:00.
- Observed minimum outbound travel: 0:07.
- Observed minimum return travel: 0:07.
- Final own-rally safety cutoff: T-5:30.
- Before T-5:30: own-rally and join work may interleave.
- From T-5:30 until event end: join-only.
- During the final five minutes: consume joinable rallies already in progress; do not poll indefinitely for new ones.
- An own formation becomes reusable only after the march row is visibly idle, never from an estimated timer alone.
- A returned joining formation becomes eligible immediately and retains its place in the configured rotation.

## Code changes expected

- `BearSessionCoordinator.java`
  - encode the T-5:30 own-rally cutoff and final join-only phase;
  - track each configured formation independently;
  - replace pause-driven polling with deadline/yield decisions;
  - preserve the pending formation across candidate races and recovery.
- `BearTrapRoutine.java`
  - remove active-session fixed sleeps;
  - keep and verify the World-at-Bear anchor;
  - add fast direct War-list transitions;
  - parse and rank rally candidates, including troop capacity and green/grey state;
  - confirm yellow formation selection and successful deployment.
- `MarchHelper.java`
  - make selection success depend on the post-tap yellow state;
  - treat slot names as irrelevant;
  - expose reliable ready/busy evidence without a fixed 300 ms wait.
- Add a small Bear rally-row parser/model rather than embedding row OCR and ranking inside the routine.
- Extend frame fixtures/templates only where colour/row evidence cannot be robustly derived from existing captures.

## Tests required before another live Bear

### Deterministic tests

- no own rally at or after T-5:30;
- existing rallies remain joinable during the final five minutes;
- one missed War-list transition or missing plus icon never counts as an empty list;
- one confirmed empty final-five-minute frame does not drain joining;
- final-window draining requires three confirmed, distinct, complete empty-list observations;
- a slow War-list transition beyond 1.5 seconds remains `PAGE_NOT_READY`, not `NO_JOINABLE_RALLY`;
- five-minute preparation plus outbound and return timing produces at most five own rallies in 30 minutes;
- a remembered three-minute selection is changed to five minutes and verified before deployment;
- an unverified timer selection prevents own deployment but does not block joining;
- green button plus open member slots still rejects insufficient troop capacity;
- grey button is never tapped;
- a full/departed candidate retries the same formation;
- configured join order is preserved across failures and returns;
- renamed formations work because selection is positional;
- a formation advances only after yellow selection and deployment confirmation;
- a failed own-slot selection immediately yields to available join work rather than restarting the full Bear path;
- nineteen consecutive own-start failures cannot starve the join path;
- cancellation during any poll prevents the next tap;
- recovery never performs a blind second Back action;
- all timing tests use a fake clock and contain no real sleeps.

### Recorded-frame tests

Use the live screenshots to cover:

- the formation-bar geometry that caused slot 1 to produce zero yellow pixels;
- selected and unselected slot 1 with the remembered three-minute timer visible;
- confirmed five-minute timer selection;
- open members but full troop capacity;
- green button becoming grey;
- yellow selected slots 1 through 6;
- busy/in-use formation markers;
- green participation marker;
- preparing, outbound, returning, and idle march states;
- old/full top rows and newer joinable lower rows.

### Next live acceptance run

The next scheduled Bear is the release gate. Capture state-transition timestamps and require:

- initial preparation reaches the Bear promptly, without the observed minute-long navigation;
- own formation selected yellow and first rally confirmed;
- every available join slot filled in configured order;
- candidate races recover without losing the formation order;
- returned formations are reused within ten seconds, preferably within the next verified frame cycle;
- no own rally begins after T-5:30;
- final five minutes are join-only and stop scanning once existing rallies drain;
- clean exit at event end with normal tasks restored.

Suggested performance targets for the live run:

- first useful Bear action after activation: under 5 seconds;
- confirmed own-rally setup from a retained World-at-Bear screen: under 5 seconds;
- known World screen to War list: under 1 second at p95;
- visible valid candidate to Deploy tap: under 1.5 seconds at p95;
- failed candidate to next candidate: under 750 ms at p95;
- returned formation to next attempt: under 5 seconds when a valid rally exists;
- full Bear navigation recovery: under 5 seconds, with the Alliance route used only as fallback.

## Implementation order

1. Preserve the 19 September log trace and sanitized frames as a deterministic replay fixture. Do not commit profile or player identifiers.
2. Write failing tests for the observed loop: remembered three-minute timer, slot-1 yellow miss, slow War transition, single-frame false drain, and own-failure starvation.
3. Add the latest-frame model, explicit screen classifier, and state/evidence telemetry.
4. Add the verified action executor and local recovery graph. Remove generic multi-step recovery from the active path.
5. Implement explicit five-minute selection and frame-derived positional formation selection.
6. Add the War-list parser/model for button colour, troop capacity, countdown, Bear identity, and confirmed empty scans.
7. Implement the pure decision engine, per-formation lifecycle tracking, join-first degradation, T-5:30 cutoff, and final-window drain evidence.
8. Run unit tests, recorded-frame tests, and a replay of today's complete failure trace. The replay must produce useful joins instead of the 19-attempt loop.
9. Exercise navigation and timer/formation selection outside the live event without deploying, using current screens and sanitized captures.
10. Build the local Elroy version only after deterministic evidence passes.
11. Use the next scheduled Bear as live acceptance. Keep the PR in draft until the live criteria pass.
