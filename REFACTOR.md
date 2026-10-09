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

**The diff and the suite.** The pin is commit `648a40c` and the refactor is commit
`ca3fb6f`, which comes right after it (`git show ca3fb6f`). Totals: `Tests run: 36,
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

- **Singleton:** `NotifierFactory` (private constructor, `getInstance()`).
- **Factory:** `NotifierFactory.createStrategy()`.
- **Strategy:** `NotificationStrategy` / `EmailNotificationStrategy`, held by `NotificationHub`.
- **Observer:** `NotificationHub` (subject) with `NotificationSubscriber` / `OutboxSubscriber`.
  `OutboxSubscriber` is also a small **Adapter** from `Outbox` to the subscriber interface.

### The problem each one solves

- **Singleton:** exactly one shared instance must exist because it owns state or
  a costly resource, such as a loaded template set or a mail connection.
- **Factory:** the concrete renderer has to be picked at runtime, from config or
  from the recipient, without callers knowing the choice.
- **Strategy:** several rendering formats exist and the hub must switch between
  them, such as email for some recipients and SMS for others.
- **Observer:** several independent receivers, unknown to the publisher, register
  for the same events, such as an outbox plus an audit log plus a mail sender.

### Which of those problems exist here

- **Singleton: no.** `NotifierFactory` has no fields, so there is no state to share.
- **Factory: no.** `createStrategy()` always returns `new EmailNotificationStrategy()`
  with no input, and its only caller is `NotificationHub`'s constructor (line 22).
- **Strategy: no.** There is one implementation, and the hub hard-wires it through
  the factory, so not even a test can swap it.
- **Observer: no.** `subscribe` is called only from the hub's own constructor (line 23),
  so there is always exactly one subscriber, which `hubDeliversToItsOneSubscriber` confirms.

### The simpler structure

**Your proposal.** Keep `NotificationMessage` and `Outbox`, and drop the rest.
`NotificationHub.publish(message)` does `outbox.append("To: " + recipient + " |
Subject: " + subject + " | " + body)` directly. That takes six types down to three.

**What stays the same.** Each `publish` appends exactly one string, in the format
`To: <recipient> | Subject: <subject> | <body>`, in order, to the same `Outbox`
returned by `getOutbox()`. That is what `publishedMessageLandsInTheOutboxFullyRendered`,
`aConfirmationFromTheWorkflowReachesTheOutbox`, and every outbox-size assertion in
`BookingWorkflowTest` check. `hubDeliversToItsOneSubscriber` and
`factoryHandsBackTheSameInstance` pin structure, not behavior, so they would go
away with the layers they test.

**What you would keep, if anything.** None of the interfaces. Each one has a
single implementation and a single caller, so it adds a hop and no choice. The
`Outbox` class stays because tests and callers read from it.

### What would bring each layer back

- **Observer:** facilities asks for every cancellation to also go to an audit log
  or a Slack channel, registered only in some deployments. Then several receivers
  would really be independent.
- **Strategy (+ Factory):** members can choose SMS instead of email, so the
  160-character format depends on a per-member setting at publish time.
- **Singleton:** rendering loads an expensive shared template bundle at startup
  that every hub must reuse.

**Misuse or anti-pattern?** It is a misuse: each pattern is implemented correctly,
but the problem it solves does not exist here (speculative generality). The
distinction matters because misuse is fixed by deleting layers until the
requirement arrives. An anti-pattern is harmful even when the requirement does
exist, and the hard-wired Singleton leans that way because it hides the hub's
dependency from tests.

---

## Milestone 3: The missing pattern

Read `pricing/`. Not coded, one sentence.

**The pattern.** Which one fits `PriceCalculator`, and the problem that makes
it fit. Name the problem.

**Would you apply it today?** Yes or no, one line, with the reason.
