# Chapter 04 — Eval and trampolining

## Why this exists

Chapter 03 ended with a program that had nothing wrong with it except its
length. `State` was a function, `flatMap` wrapped one function in another, and
running a loop of ten thousand steps called ten thousand functions nested
inside each other:

```text
java.lang.StackOverflowError
```

Nothing about the program was recursive in a way the code made visible. The
recursion was in the *interpreter* — in how a chain of `flatMap`s is run — and
the JVM gives every thread a stack of a fixed size. How many calls fit depends
on the frames and on how warm the JIT is, so the depth at which a program
breaks is not even a constant — but it is finite, and the plainest recursive
functions reach it:

```scala
def isEven(n: Int): Boolean = if n == 0 then true else isOdd(n - 1)
def isOdd(n: Int): Boolean = if n == 0 then false else isEven(n - 1)

isEven(1_000_000) // StackOverflowError
```

`@tailrec` does not help: the two calls are in tail position, but in two
different methods, and Scala only turns a method's calls *to itself* into a
loop. A hand-written `foldRight` over a long list has the same problem. The
standard library's avoids it, by folding the reversed list from the left, and
has a second one: a right fold that could stop early — `exists`, finding a
`true` — cannot, because the rest of the list is folded before the function
sees the current element.

Each of these is a computation whose *shape* is fine and whose *execution*
spends a stack frame per step. The fix in every case is to stop executing the
steps as calls, and describe them as data instead: a value that says "do this,
then that", which a loop can walk with constant stack.

## The idea

**A trampoline returns the next step instead of taking it.** A function that
would call another function returns a description of the call — `More(() =>
isOdd(n - 1))` — and a loop, the trampoline, keeps calling the description
until it gets a finished value, `Done(b)`. The loop is a single
`@tailrec` method, so it runs in one stack frame; the recursion has moved from
the stack, where it is limited, to the heap, where it is not.

**`flatMap` is a step too.** `More` and `Done` are enough for tail calls. For
a step whose result feeds the next — every monadic program — the trampoline
needs a third case, `FlatMap(source, f)`, and the loop needs one more rule:
when the source is itself a `FlatMap`, *re-associate*,

```text
FlatMap(FlatMap(s, f), g)   becomes   FlatMap(s, x => FlatMap(f(x), g))
```

which is the monad associativity law used as a rewrite. A left-nested chain of
a million binds becomes right-nested one step at a time, without the loop
ever recursing into the source. That rule is the whole difference between a
`flatMap` that is stack-safe and one that is not.

**`Eval` is a trampoline with evaluation strategies.** Its leaves say *when* a
value is computed:

| constructor | like | computed | cached |
| --- | --- | --- | --- |
| `Eval.now(a)` | `val` | right away | — it is already a value |
| `Eval.later(a)` | `lazy val` | on the first `.value` | yes |
| `Eval.always(a)` | `def` | on every `.value` | no |
| `Eval.defer(fa)` | — | builds `fa` on `.value` | no |

`map` and `flatMap` build `FlatMap` nodes, and `.value` is the trampoline. An
`Eval` is a description: building one runs nothing (except `now`, which got a
value already computed), and a chain of a million `flatMap`s is a million
small objects on the heap, walked by one loop.

**`defer` is where the stack is saved.** A recursive function written with
`Eval` must not call itself while *building* its result. `foldRight(t)`
evaluated as an argument, before `Eval` is involved, recurses as deeply as the
list is long, and overflows before `.value` is ever reached. `Eval.defer(
foldRight(t))` returns at once, and the call happens later, inside the loop.
The same holds for `State` run on `Eval` — `S => Eval[(S, A)]` — whose
`flatMap` must not call the first program's `run` directly: it defers it, and
then a million `modify` steps are a million heap nodes instead of a million
frames.

**Laziness gives `foldRight` its short-circuit back.** With the rest of the
fold passed as an `Eval[B]` instead of a `B`, the function decides whether to
look at it. `exists` returns `Eval.now(true)` at the first match and never
asks for the rest; with a strict `B`, the rest was already computed before the
function was called.

## In code

The executable version of this chapter is
[`Walkthrough.scala`](../../modules/exercises/src/main/scala/advfp/ch04evalandtrampolining/Walkthrough.scala).
Read it alongside this document. It compiles with the rest of the module, so
none of it is pseudo-code, and `sbt playground/console` is where to poke at it.

## Pitfalls

**A run loop that runs the source first is not a loop.** The natural code
for a `FlatMap` is "run the source, feed the value to `f`, run that". Its
first `run` is not in tail position, and `@tailrec` says so:

```text
Cannot rewrite recursive call: it is not in tail position
```

Without the annotation it compiles and passes every test of a few hundred
steps. Exercise 01's spec at a million fails both chains, left- and
right-nested, and munit reports the overflow as an ordinary test failure:

```text
java.lang.StackOverflowError: null
```

Annotate every run loop with `@tailrec`. The compiler then rejects the
version that needs a stack, instead of a test finding it at the right depth.

**`isEven` without `More` overflows while it is built.** `isEven` returning
`isOdd(n - 1)` directly has the right type and recurses before `run` is ever
called. Measured 2026-10-17: only the spec at a million fails. Writing `More`
in one of the two functions and not the other passes the whole spec, and
rightly: every other step returns to the loop, so the stack never holds more
than one direct call. A trampoline needs one bounce per cycle of the
recursion, not one per function.

**The depth that breaks is not a constant.** Measured 2026-10-17 in sbt's own
JVM (4 MB stack) with a warm JIT: the plain `isEven` overflowed at 429,688,
chapter 03's `State` looped with `foldLeft` at 172,121 ticks, and a
right-recursive `forEach` on it at 64,241 elements. A cold JVM, or a thread
with the default 1 MB stack, gives up far earlier. That is why the specs run at
a million: a test at ten thousand passes for a naive version on a good day.

**`defer` goes around the recursive call, not around the result.**
`Eval.defer(f(a, foldRight(rest, z)(f)))` looks lazy and is not: the argument
`foldRight(rest, z)(f)` is still evaluated, one call per element, before
`f` is called. Measured 2026-10-17: exercise 03 without a `defer` on the
recursive call fails five of ten tests — both million-element ones with
`java.lang.StackOverflowError`, and the three that check `f` sees the elements
in order and stops early.

**The standard library's `foldRight` is stack-safe and still wrong.** It
reverses the list and folds from the left, so a million elements run. It also
calls `f` on every element, last one first, before anything is run: `exists`
written on it calls `p` on every element of `List(1, 2, 3, 4)` looking for `1`
(four calls, measured). Only a test that watches the order of the calls sees
it; exercise 03's spec has three.

**`State` on `Eval` needs the `defer` in one place only.** Without
`Eval.defer` around the first program's `run`, `flatMap` still runs the
second program inside the trampoline, and right-nested chains, `forEach` and
every law pass. Only a left-nested chain — exactly the `foldLeft` that broke
chapter 03 — overflows, in `.run` instead of in `.value`. Measured 2026-10-17:
one failure of fourteen in exercise 04's spec.

**A `later` keeps what its thunk captured.** The thunk is a closure, and the
`lazy val` that holds the result does not let go of it. `cats.Eval` clears it
after the first evaluation, with a `var`; the `Eval` of this chapter keeps the
type immutable and accepts the leak. Something to remember before putting a
`later` over a large structure into a long-lived value.

## Exercises

| # | asks for |
| --- | --- |
| 01 | a `Trampoline` with its run loop and `Monad`, and `isEven` / `isOdd` that do not overflow |
| 02 | `Eval` with `now`, `later`, `always` and `defer`, memoized where it promises to be, stack-safe under a million binds |
| 03 | a lazy `foldRight` on `Eval`, and an `exists` that stops at the first match |
| 04 | `State` run on `Eval`, stack-safe where chapter 03's overflowed |

```bash
sbt "exercises/testOnly advfp.ch04evalandtrampolining.*"
```

## Further reading

- Rúnar Óli Bjarnason, *Stackless Scala With Free Monads*, 2012 — the
  trampoline, the re-association of `FlatMap`, and `State` made stack-safe on
  top of it.
- Steven E. Ganz, Daniel P. Friedman and Mitchell Wand, *Trampolined Style*,
  ICFP 1999 — the trampoline as a program transformation, and why it gives
  tail calls to a language without them.
- The `cats.Eval` source, `core/src/main/scala/cats/Eval.scala` in
  typelevel/cats — the production version of exercise 02, with the memoization
  and the run loop this chapter builds by hand.
