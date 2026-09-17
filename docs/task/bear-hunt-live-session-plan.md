# Bear Hunt live-session plan

Status: approved on 17 September 2026. Deterministic implementation is complete; the next scheduled Bear Hunt remains the live acceptance gate.

## Goal

Run the full 30-minute Bear Hunt as one fast, state-aware session:

- start up to five own rallies when the timing permits;
- continuously fill every configured joining march in the user's configured order;
- reuse a formation as soon as its march returns;
- keep trying after full, departed, or stale rallies;
- stop creating own rallies at `event end - 5:30`, while continuing to join rallies already in progress;
- finish cleanly at the event end without disturbing unrelated tasks.

The active Bear loop must not contain blind fixed sleeps. Screen capture and verified state changes are the pacing mechanism. Longer waits for a march return must yield to a scheduled deadline and remain cancellable; they must not hold the profile in a sleeping navigation routine.

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
- an empty final-five-minute list ends scanning without waiting for new rallies;
- five-minute preparation plus outbound and return timing produces at most five own rallies in 30 minutes;
- green button plus open member slots still rejects insufficient troop capacity;
- grey button is never tapped;
- a full/departed candidate retries the same formation;
- configured join order is preserved across failures and returns;
- renamed formations work because selection is positional;
- a formation advances only after yellow selection and deployment confirmation;
- cancellation during any poll prevents the next tap;
- recovery never performs a blind second Back action;
- all timing tests use a fake clock and contain no real sleeps.

### Recorded-frame tests

Use the live screenshots to cover:

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

- known World screen to War list: under 1 second at p95;
- visible valid candidate to Deploy tap: under 1.5 seconds at p95;
- failed candidate to next candidate: under 750 ms at p95;
- returned formation to next attempt: under 5 seconds when a valid rally exists;
- full Bear navigation recovery: under 5 seconds, with the Alliance route used only as fallback.

## Implementation order

1. Lock the timing model and tests, including T-5:30 and join-only behavior.
2. Add rally-row perception for button colour, troop capacity, countdown, and Bear identity.
3. Replace blind formation selection with post-tap yellow confirmation.
4. Replace repeated navigation with persistent screen state and the direct War path.
5. Remove fixed sleeps from the active path and introduce bounded fresh-frame transitions.
6. Wire per-formation lifecycle tracking and immediate retry/reuse.
7. Run unit and recorded-frame tests.
8. Build the local Elroy version and perform the next live Bear acceptance run.
9. Only after live evidence passes, update the draft PR and mark it ready for review.
