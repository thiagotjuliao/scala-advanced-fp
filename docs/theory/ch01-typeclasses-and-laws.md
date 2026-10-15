# Chapter 01 — Typeclasses and laws

## Why this exists

Folding a list needs two things the list does not have: a starting value and a
way to combine two elements. `List(1, 2, 3).foldLeft(0)(_ + _)` passes both by
hand, and so does every other fold in the program, each time — and the `0` and
the `_ + _` have to agree with each other, which nothing checks. Pass `1`
instead of `0` and the sum is off by one; pass `0` with `_ * _` and every
product is zero.

A typeclass names the pair once. `Monoid[A]` *is* "a starting value and a
combination for `A`", so `combineAll[A: Monoid](as: List[A])` folds anything
that has one, and the pair travels together. That is the operational half.

The other half is that the pair has to *behave*. `combineAll` splits nothing
and reorders nothing, but code that does — a parallel fold, a map-reduce, the
partial aggregates of a Spark `groupBy` — combines in whatever grouping it
likes and starts each chunk from the identity. It is correct only if the
grouping cannot change the result and the identity really is neutral. Those
are the laws, and no type states them. This chapter is about both halves, and
about the second one more.

## The idea

A typeclass is a trait with a type parameter; an instance is a value of that
trait for a particular type; and `using` parameters (or a context bound,
`A: Monoid`) let the compiler supply the instance at each call site. In this
course the chapter's three typeclasses live in `Typeclasses.scala`:

| typeclass | operations | laws |
| --- | --- | --- |
| `Semigroup[A]` | `combine`, written `x \|+\| y` | associativity |
| `Monoid[A]` | `combine`, `empty` | associativity; left and right identity |
| `Functor[F[_]]` | `fa.map(f)` | identity; composition |

**Laws are the specification.** `Semigroup[Int]` with subtraction compiles,
and `(1 - 2) - 3` is `-4` while `1 - (2 - 3)` is `2`. The type checker has no
objection; a law check on samples does. So every instance in this course comes
with law checks, and a law check is only as good as its samples — exercise 04
is about samples that cannot see a wrong instance at all.

**One instance per type.** Implicit search looks for *the* `Monoid[Int]`.
Integers have several lawful monoids — addition, multiplication, max, min — and
a single `given Monoid[Int]` would choose one for every caller in the program.
The Scala 3 answer is an **opaque type**: `opaque type Sum = Int` is a distinct
type outside its defining scope, with the representation of an `Int` and no run
time cost. One opaque type per monoid, one instance each, and nothing answers
`Monoid[Int]` — the caller has to say which.

**Where implicit search looks.** First the call site's own scope: definitions
and imports. Then the *implicit scope* of the type being searched for — the
companion objects of its parts. An instance in the companion of `Sum` is found
wherever a `Sum` is, with no import. An instance for `Map[K, V]` defined in some
`object Instances` is in no companion of `Map`, `K`, `V` or `Monoid`, so it has
to be imported — with `import Instances.given`, because a wildcard import does
not bring givens in.

**Functors** are the first typeclass over a type *constructor*: `Functor[F[_]]`
takes `List`, `Option`, a tree — not `List[Int]`. `map` transforms the contents
and keeps the structure, and the two laws say exactly that: mapping the
identity changes nothing, and mapping twice is mapping the composition once.
A type with two parameters, like `Either[E, A]`, gets a functor by fixing the
first: `[A] =>> Either[E, A]`, a type lambda Scala 3 writes natively.

## In code

The executable version of this chapter is
[`Walkthrough.scala`](../../modules/exercises/src/main/scala/advfp/ch01typeclassesandlaws/Walkthrough.scala).
Read it alongside this document. It compiles with the rest of the module, so
none of it is pseudo-code, and `sbt playground/console` is where to poke at it.

## Pitfalls

**An opaque type is transparent where it is defined.** Inside the object that
declares `opaque type Max = Int`, `Max(3)` is just an `Int`, so the companion of
`Max` is not part of the search, and an extension method from its instance is
not found:

```text
value |+| is not a member of advfp.ch01typeclassesandlaws.Walkthrough.Max, but could be made available as an extension method.
```

The instance is fine; the call site is in the wrong place. Use the type from
outside its defining object — the walkthrough nests it in `object numbers` for
this reason.

**A wildcard import does not import givens.** `import Instances.*` brings in
everything except the instances, and the compiler says so:

```text
No given instance of type advfp.ch01typeclassesandlaws.Monoid[Map[String, Int]] was found for parameter x of method summon in object Predef

Note: given instance m in object Instances was not considered because it was not imported with `import given`.
```

`import Instances.given` brings in the givens; `import Instances.{*, given}`
brings in both.

**`given … with` is gone.** The Scala 2-flavoured `given x: T with` body is
rejected under `-source:future`, which this course's answer key compiles with:

```text
Given member definitions starting with `with` are no longer supported; use `{...}` or `:` followed by newline instead
```

Write `given treeFunctor: Functor[Tree]:` followed by an indented body.

**A member method beats an extension.** `Either` has its own `map`, so
`Right(1).map(f)` calls `Either.map` and never reaches a `Functor` instance. To
exercise an instance, call it through the instance: `Functor[F].map(fa)(f)`.

**An identity has laws of its own.** `Monoid.instance(0)(_ * _)` passes
associativity on every sample and multiplies any two values correctly; it is
wrong only where the fold starts from `empty`. Associativity says nothing about
the identity — check both identity laws, on both sides.

**Samples can be blind.** A functor that reverses its list keeps the identity
law on `Nil`, on every single-element list and on every palindrome. Choose
samples against the bug you fear: for order, two different elements.

## Exercises

| # | asks for |
| --- | --- |
| 01 | Two monoids on `Int`, addition and multiplication, kept apart by opaque types; and `combineAll` |
| 02 | Monoids for `Map[K, V]` and `Option[A]` built from a semigroup on the values |
| 03 | Functors for a binary tree and for `Either[E, *]` via a type lambda |
| 04 | The shortest counterexample to a lawless functor, and the samples that cannot see it |

```bash
sbt "exercises/testOnly advfp.ch01typeclassesandlaws.*"
```

## Further reading

- Philip Wadler and Stephen Blott, *How to make ad-hoc polymorphism less ad
  hoc*, POPL 1989 — where typeclasses come from.
- Philip Wadler, *Theorems for free!*, FPCA 1989 — what a polymorphic type
  promises on its own, and why a lawful functor identity implies composition.
- [Opaque types](https://docs.scala-lang.org/scala3/book/types-opaque-types.html),
  Scala 3 book, and [SIP-35](https://docs.scala-lang.org/sips/opaque-types.html),
  the proposal that introduced them.
- [Importing givens](https://docs.scala-lang.org/scala3/book/ca-given-imports.html),
  Scala 3 book.
