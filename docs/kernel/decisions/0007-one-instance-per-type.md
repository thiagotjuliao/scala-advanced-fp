# 0007 — One instance per type, in the root's companion

2026-10-29 · M1 · accepted

## Context

`Monoid extends Semigroup`, and later `Monad` and `Traverse` both extend
`Functor`. A type with several typeclasses of one hierarchy (`Int` is a
`Semigroup` and a `Monoid`, `Option` a `Monad` and a `Traverse`) can end up
with one instance per typeclass, and nothing makes them agree. Five cases were
compiled in `playground` on 2026-10-29 (Scala 3.9.0):

| where the givens are | result |
| --- | --- |
| `Monoid[Int]` in `object Monoid` only | `summon[Semigroup[Int]]` does not compile: "No given instance of type probe.b.Semigroup[Int] was found". The search for `Semigroup[Int]` does not look in a subclass's companion. |
| `Monoid[Int]` in `object Semigroup` | `summon[Semigroup[Int]]` and `summon[Monoid[Int]]` return the same object. |
| `Semigroup[Int]` in `object Semigroup`, a multiplying `Monoid[Int]` in `object Monoid` | compiles without a warning. `Semigroup[Int]` adds, `Monoid[Int]` multiplies. |
| `Monad[Option]` and `Traverse[Option]` as two givens in `object Functor` | `summon[Functor[Option]]` fails: "Ambiguous given instances: both object given_Monad_Option in object Functor and object given_Traverse_Option in object Functor match type probe.two.Functor[Option]". |
| one given of type `Monad[Option] & Traverse[Option]` in `object Functor` | `Functor`, `Monad` and `Traverse` all resolve, to that one object. |

The third case is the dangerous one: two lawful instances that disagree, and
no error. The fourth is chapter 02's pitfall "Two instances, two `map`s",
which comes back as soon as `Traverse` exists.

## Decision

- **One instance per data type and hierarchy.** It implements every
  typeclass of that hierarchy the type has: `Monoid[Int]`, not
  `Semigroup[Int]` plus `Monoid[Int]`, and one object that is
  `Monad[Option] & Traverse[Option]`.
- **It lives in the companion of the hierarchy's root**: `object Semigroup`
  for `Semigroup` and `Monoid`, `object Functor` for `Functor`, `Applicative`,
  `Monad`, `Foldable` and `Traverse`. A search for any typeclass of the
  hierarchy looks in its superclasses' companions, so every search finds the
  same object, with no import.
- `Eq` is a hierarchy of one, and stays in `object Eq`.

## Alternatives

- **A separate `instances` package, imported where needed**, as Cats did
  before 2.2. It depends on imports: `import instances.*` does not bring
  givens (chapter 01, pitfalls), and two packages covering one type are
  ambiguous. It also does not prevent two instances per type. Cats 2.2 moved
  its instances into implicit scope, and kept the package for compatibility.
- **Each instance in the companion of its own typeclass.** The first and
  third cases above: either a supertype's search misses it, or two instances
  exist and disagree silently.
- **Prioritised givens** (low-priority traits, as Cats did with implicits).
  It chooses between two instances; it does not make them agree.

## Consequences

The `Semigroup` givens for `Int`, `Option` and `List` become `Monoid` givens
in `object Semigroup`, with the same `combine`. `Either` has no lawful
`Monoid` with only a `Semigroup[A]`, and stays a `Semigroup`.

When a subclass's instance needs stronger evidence than the superclass's (a
`Monoid[Either[E, A]]` would need `Monoid[A]`, where the `Semigroup` needs
only `Semigroup[A]`), one object cannot serve both, and two givens are
unavoidable. M1 does not need such an instance. If one is needed, it gets its
own decision, with what the compiler does when both givens match.

The tests check coherence directly, for each type, at every level of the
hierarchy. A given without parameters is one object, so the summoned
instances are compared with `eq`. A given with parameters (`Monoid[Option[A]]`)
builds a new object on every search, so `eq` would fail on a coherent instance.
There the test compares the instances' classes.

## Addendum — a second root

2026-10-30 · amends the decision above

`Foldable` does not extend `Functor`, so the `Functor` hierarchy is not a
tree: `Traverse` will extend both, and a search for `Foldable[Option]` looks in
`object Foldable` only. Measured in `playground` the same day (Scala 3.9.0):

| where the givens are | result |
| --- | --- |
| one `Traverse[Option]` in `object Functor` | `summon[Traverse[Option]]` compiles; `summon[Foldable[Option]]` does not: "No given instance of type probe.without.Foldable[Option] was found". |
| `given optionInstances: (Monad[Option] & Traverse[Option])` in `object Functor`, and `given Foldable[Option] = Functor.optionInstances` in `object Foldable` | `Functor`, `Monad`, `Foldable` and `Traverse` of `Option` all resolve, all `eq` to `optionInstances`. |

So the rule becomes:

- The one object per type lives in `object Functor`, as a **named** given, so
  it can be referred to.
- Each other root (`Foldable` for now) gets a given **alias** to that object,
  typed as that root only (`Foldable[Option]`, not the intersection). An alias
  is not a second instance: it evaluates to the same object.
- Until `Traverse` exists, a type that is `Foldable` but not yet `Traverse`
  may have its own `Foldable` instance in `object Foldable`; the two
  hierarchies share no method, so they cannot disagree. When `Traverse`
  arrives, that instance becomes the alias.

The coherence tests add the second root: once a type is a `Traverse`, its
`Foldable` and its `Functor` are the same instance, compared with `eq` or by
class as above.

## Addendum — types of our own

2026-10-30 · amends the decision above

The rule above is written for the standard types, whose companions are not
ours to add to. A type defined in this repository (`Tree`, then `Gen`) keeps
its one instance in its own companion: the companion of a type argument is in
the implicit scope of every typeclass of that type, so every search finds the
same object there, with no import and nothing added to `object Functor`. The
coherence tests are the same.
