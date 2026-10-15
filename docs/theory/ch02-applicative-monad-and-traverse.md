# Chapter 02 — Applicative, Monad and Traverse

## Why this exists

Chapter 01 ended with `Functor`, and `map` takes a function of one argument.
Parse two fields of a form, each into an `Option[Int]`, and try to add them:

```scala
val width: Option[Int] = "3".toIntOption
val height: Option[Int] = "4".toIntOption
width.map(w => (h: Int) => w * h) // Option[Int => Int] — and then?
```

The function is half applied and stuck inside an `Option`. A functor has no
operation that takes a function *in the context* and a value *in the context*,
so every program that combines two independent results — two fields, two
lookups, two table reads — has to leave the abstraction and match by hand.
`Applicative` adds exactly that operation, `ap`, and from it every arity.

Then there are programs where the second step depends on the first result:
look up a user, then look up *that user's* team. No arrangement of `ap` can
write it, because `ap` fixes both computations before either runs. `Monad`
adds `flatMap`, which computes the next step from the previous value.

And once single values combine, so do whole structures. Parse every label of a
tree, and the answer should be `Option[Tree[Int]]` — the tree if every label
parses, `None` otherwise — not a `Tree[Option[Int]]` to inspect by hand.
`Traverse` is that walk, written once for every applicative.

## The idea

The chapter's hierarchy lives in `Typeclasses.scala`, with only the primitive
operations. Everything else is derived, and the derivations are the exercises.

| typeclass | primitives | free | laws |
| --- | --- | --- | --- |
| `Functor[F]` | `map` | | identity, composition |
| `Applicative[F]` | `pure`, `ap` | `map` | identity, homomorphism, interchange, composition |
| `Monad[F]` | `flatMap` | | left and right identity, associativity, `ap` agrees with `flatMap` |
| `Foldable[F]` | `foldRight` | `toList` | |
| `Traverse[F]` | `traverse` | `map`, `sequence` | identity, effects in `foldRight` order |

**`ap` is application, one argument at a time.** `pure(f).ap(fa)` is `map`. A
function of two arguments is curried into a function that returns a function:
map it over the first value, `ap` the second. Three arguments, one more `ap`.
`map2`, `product` and `mapN` are that construction with a name, and they hold
for every applicative because they mention none.

**`flatMap` versus `ap` is a statement about what is known when.** In
`ff.ap(fa)` both computations exist before anything runs; the result can depend
on their values, but which effects happen cannot. In `fa.flatMap(f)` the second
computation does not exist until `f` has seen the first value. That is why
every monad is an applicative — `ap` is `ff.flatMap(f => fa.map(f))` — and why
not every applicative is a monad: the extra power has to come from somewhere.

**A monad's `ap` is not free.** A type can have several lawful applicatives.
Lists can cross every function with every value, or zip them position by
position; both obey the four applicative laws. Only crossing is
`ff.flatMap(f => fa.map(f))`, so only crossing may be the `ap` of the list
monad. Code generic in `Monad[F]` mixes `ap` and `flatMap` freely, and is
correct only if the two agree. An `ap` that does something more useful than its
`flatMap` — accumulating every error instead of stopping at the first — is
lawless *as a monad's*, however good it is on its own. Chapter 03 builds that
applicative, `Validated`, and gives it no `flatMap`.

**An applicative that is not a monad.** `ZipList`'s `ap` zips, and its `pure`
must be infinite, or `pure(identity).ap(v)` would cut `v` down to one element.
The natural `flatMap` takes the diagonal — the `i`-th element of `f` applied to
the `i`-th element — and it agrees with `ap`. But finite lists have lengths,
the diagonal has to stop where one runs out, and nesting the binds differently
consults different lengths: associativity breaks. Exercise 04 finds the
counterexample.

**`Traverse` is `map` with an effect.** `traverse(f)` runs `f` at every element,
collects the effects with `ap`, and rebuilds the structure inside them. It asks
for an `Applicative[G]` and no more, because the walk is decided by the
structure, never by a value found on the way. With `G = Id`, no effect at all,
`traverse` *is* `map`, which is how `Traverse` gets `map` for free. With
`G = Option` the result is `None` as soon as one element fails. `sequence` is
`traverse(identity)`: it turns `F[G[A]]` inside out into `G[F[A]]`.

## In code

The executable version of this chapter is
[`Walkthrough.scala`](../../modules/exercises/src/main/scala/advfp/ch02applicativemonadandtraverse/Walkthrough.scala).
Read it alongside this document. It compiles with the rest of the module, so
none of it is pseudo-code, and `sbt playground/console` is where to poke at it.

## Pitfalls

**Two instances, two `map`s.** `Monad[Box]` and `Traverse[Box]` both extend
`Functor[Box]`, so both bring an extension `map`. Given at the same level,
`.map` on a box finds both and takes neither:

```text
Note that implicit extension methods cannot be applied because they are ambiguous;
both object boxMonad in object Walkthrough and object boxTraverse in object Walkthrough provide an extension method `map` on advfp.ch02applicativemonadandtraverse.Walkthrough.Box[A]
```

The two agree — the laws make sure of it — but the compiler cannot know. Call
through the instance (`boxMonad.map(box)(f)`), or keep one instance in a nested
scope, where the nearer one wins; the walkthrough does the second.

**`traverse` cannot `flatMap`.** It gets an `Applicative[G]`, and reaching for
`flatMap` inside it is rejected:

```text
value flatMap is not a member of G[Int]
```

That rejection is the design, not an obstacle. Combine with `map` and `ap`.

**Short-circuiting is in the result, not in the work.** `traverse` with
`Option` returns `None` once any element fails, and still calls `f` on every
element: traversing `Rose("x", List(leaf("1"), leaf("2"), leaf("y")))` with
`_.toIntOption` calls it four times, although the root has already failed. The
walk is fixed before it starts — an applicative cannot stop it. Stopping early
needs `flatMap`, or laziness (chapter 04).

**Order is part of `ap`.** `fb.map(b => (a: A) => f(a, b)).ap(fa)` has the type
of `map2` and the right value, and runs `fb`'s effect before `fa`'s. On `Option`
nothing shows it. On a type whose effect is visible — the `Logged` of exercise
02, the error of an `Either` — it is a different, wrong `map2`. Test order on an
effect that records it.

**Two equal infinite lists never finish comparing.** `ZipList`'s `pure` is
infinite, and `LazyList` equality walks both lists to the first difference.
`pure(1) == pure(2)` is `false` at once; `pure(1) == pure(1)` does not return.
Compare a prefix (`values.take(50).toList`) whenever a side may be infinite —
the spec of exercise 04 does.

**A law needs samples that can fail it.** A `List` `ap` with the loops swapped
passes every applicative law; only the agreement with `flatMap` catches it, and
only on a sample with at least two functions and two values. An `Either` `ap`
that keeps the value's `Left` is caught only when *both* sides are `Left`.

## Exercises

| # | asks for |
| --- | --- |
| 01 | `Monad` for `Option`, `Either[E, *]` and `List`, with `ap` agreeing with `flatMap` |
| 02 | `map2`, `product` and `mapN` derived from `ap`, and `ap` derived from `flatMap` |
| 03 | `Traverse` for a rose tree, and parsing every label with `Option` as the short-circuit |
| 04 | `ZipList`, an applicative that is not a monad, and the counterexample to its `flatMap` |

```bash
sbt "exercises/testOnly advfp.ch02applicativemonadandtraverse.*"
```

## Further reading

- Conor McBride and Ross Paterson, *Applicative programming with effects*,
  JFP 2008 — where `Applicative` comes from, `ZipList` and `traverse` included.
- Philip Wadler, *Monads for functional programming*, 1995 — the monad laws,
  and why a program written with `flatMap` can be refactored at all.
- Jeremy Gibbons and Bruno Oliveira, *The essence of the Iterator pattern*,
  JFP 2009 — `traverse` as the abstraction of a walk, and its laws.
- Mauro Jaskelioff and Ondřej Rypáček, *An investigation of the laws of
  traversals*, MSFP 2012 — what the `Traverse` laws actually guarantee.
