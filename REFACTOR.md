# REFACTOR.md

One section per milestone. Fill each one in as you go, in order.

Milestone 1 is written in two sittings, the pin before the refactor and the
rest after. A pin written afterwards is worth nothing, and a TA will ask.

Keep it short and specific. Point at methods, call sites, and test names.

---

## Milestone 1: Direct a refactor, characterization first

### The pin (write this section before you direct the refactor)

**The pin.** `BookingWorkflowCharacterizationTest.recurringSubmitSkipsAnOccurrenceThatOnlyTouchesAnExistingBooking`
pins that `submit` on a RECURRING request skips a week whose slot only *touches*
an existing booking (9:00-10:00 next to 10:00-11:00), returning 1 booked, 1 skipped,
and the message `series S-1: 1 booked, 1 skipped`. It is a new test class, green on
the shipped code, and no existing test method was edited.

**Why that one, and does a shipped test already cover it?** RECURRING checks room
conflicts with `<=` while REGULAR and BLOCKED use `<`, so the three overlap checks
look like duplicates but are not. A refactor that merges them into one helper would
silently change this. The closest shipped tests (`regularSubmitAcceptsASlotThatStartsWhenAnotherEnds`,
`recurringSubmitBooksEveryWeekOfAnOpenSeries`) cover the REGULAR boundary and a
conflict-free series only; I checked every `recurring*` test in `BookingWorkflowTest`.

**What a regeneration would do differently here.** It would decide again whether
back-to-back slots conflict, and it would almost certainly pick one rule (`<`) for
every type, so recurring series would start accepting touching slots.

### The directive

**The refactor and the exact directive.** Replace Conditional with Polymorphism.
I limited the scope to `workflow/` because every caller and test goes through
`BookingWorkflow`'s public API, so the per-type logic can move behind it without
any other package noticing. Directive given to the agent (Claude Code, Opus 5.5 subagent):

```
Refactor: Replace Conditional with Polymorphism in BookingWorkflow.
All four public methods (submit, cancel, priceOf, describe) switch on BookingType.
Remove all four switches by moving each type's branch into one class per type that
implements a package-private interface BookingTypeHandler (RegularBookingHandler,
RecurringBookingHandler, BlockedBookingHandler). BookingWorkflow keeps its public API
and constructor exactly as-is, does the shared pre-work each method does today (null
request check, unknown room, booking lookup, room-name fallback) in the same order,
then dispatches through an EnumMap<BookingType, BookingTypeHandler>.

SCOPE:
- In bounds: only src/main/java/edu/cmu/cs214/scheduling/workflow/.
- Out of bounds: domain/, notify/, pricing/, reporting/, everything under src/test/,
  pom.xml, docs. Do not add an abstract method to BookingType or change any enum.
- Why: every caller and test goes through BookingWorkflow's public API.

BEHAVIOR MUST BE IDENTICAL, including quirks:
- RECURRING's room-conflict check uses <= (touching slots count as taken); REGULAR and
  BLOCKED use <. Do NOT unify these into one helper.
- RECURRING submit does not check the member's other bookings; REGULAR does. Keep the
  order of every check, every message string, every notification, and the order of
  nextBookingId()/nextSeriesId() calls.
- RECURRING cancel cancels this and every later occurrence; priceOf sums non-cancelled.
Move code rather than rewrite it. Verify with mvn -B test (36 green). Do NOT commit.
```

### The result

**The diff and the suite.** The refactor is its own commit, right after the pin
commit (`git show HEAD~n` / the GitHub commit view). Totals: `Tests run: 36,
Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS` (35 shipped + 1 pin).

**What did NOT change: behavior and files.** Each handler is the old `case` body
moved line for line: RECURRING keeps `<=`, check order and message strings match,
and the pin still passes. When I temporarily changed RECURRING to `<`, the pin
failed (`expected: <1> but was: <2>`). `git diff --stat` on `domain/ notify/
pricing/ reporting/ src/test pom.xml` against the pin commit is empty, so the agent
stayed in scope.

**One thing the agent changed that you had to look at twice.** It dropped the
unreachable `default:` branches, so a future `BookingType` with no handler would
throw an NPE instead of returning "unsupported booking type". I accepted that
because all three enum values are mapped. I also removed the `calculator`/`hub`
fields it left unread in `BookingWorkflow`.

### The closing explanation

**Refactor or regenerate?** Refactoring was the right call. **Coverage:** the 18
shipped tests pin mostly happy paths, not the `<=` boundary, cancel-forward, or
the missing member check on series. **Age:** the class carries decisions with no
recorded reasons. **Spec:** the only spec is a javadoc line and the README. **Reach:**
every store write and notification goes through it, and `NotificationHubTest`
asserts exact strings. A regeneration would quietly re-decide all of that.

**What would flip your answer.** If a written spec plus tests pinned each type's
overlap rule, cancel scope, pricing, and every message string, regenerating would
be cheap and safe.

---

## Milestone 2: The pattern critique

Read `notify/`. It works and the outbox tests pass.

### The patterns present

List every design pattern you can name in that package. For each one, the class
or classes that carry it.

### The problem each one solves

For each pattern you listed, what would have to be true about the requirements
for that pattern to be the right call? One sentence each, not in terms of
"flexibility".

### Which of those problems exist here

For each pattern, does the problem it solves exist in this codebase? Point at
the code that settles it.

### The simpler structure

**Your proposal.** What replaces `notify/`. Sketch the classes and the one
method that matters.

**What stays the same.** The tested behavior it must still produce, named
precisely enough that a reader can check it against the shipped tests.

**What you would keep, if anything.** If you would keep one interface, say
which and why. "None of it" is a fine answer if you can defend it.

### What would bring each layer back

For at least two of the layers you would remove, what requirement, if it
arrived next sprint, would make that layer the right structure? Be specific
about the requirement, not about the pattern.

**Misuse or anti-pattern?** Say which this is and why the distinction matters.

---

## Milestone 3: The missing pattern

Read `pricing/`. Not coded, one sentence.

**The pattern.** Which one fits `PriceCalculator`, and the problem that makes
it fit. Name the problem.

**Would you apply it today?** Yes or no, one line, with the reason.
