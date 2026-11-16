# References

The papers and books behind the course, mapped to the chapters and milestones
that use them. Each chapter's *Further reading* section says why a reference
matters there; this file says where the reference is.

## Where the files are

| folder | in git | what goes there |
| --- | --- | --- |
| `open/` | yes | references whose licence allows redistribution (Creative Commons, BSD), with the licence recorded below |
| `local/` | no, ignored | personal copies of everything else: author preprints, publisher versions, freely downloadable but not redistributable |

The repository is public, so a PDF is committed only when its licence says it
may be shared. Everything else is kept as a link here and as a local copy for
offline reading. To fill `local/` on a new machine, download the links marked
**local** below into it, under the file name given.

**Status** says how the reference can be read:

- **open**: committed in `open/`, under the licence shown;
- **local**: free to download from the link, kept only in `local/`;
- **link**: free to read online but not as a single file (a website, source code);
- **paid**: not freely available, listed for completeness;
- **missing**: no free copy was found or the server did not answer; worth another try.

## Books

| reference | status | file / link |
| --- | --- | --- |
| Noel Welsh, *Functional Programming Strategies in Scala with Cats*, draft built 2026-01-21 | open, CC BY-SA 4.0 | `open/welsh-2026-functional-programming-strategies-draft.pdf` — [scalawithcats.com](https://scalawithcats.com) |
| Noel Welsh and Dave Gurnell, *Scala with Cats*, 2020 | open, CC BY-SA 3.0 | `open/gurnell-welsh-2020-scala-with-cats.pdf` — [scalawithcats.com](https://scalawithcats.com/dist/scala-with-cats-1.pdf) |
| Bartosz Milewski, *Category Theory for Programmers*, Scala edition, v1.3.0 | open, CC BY-SA 4.0 | `open/milewski-2019-category-theory-for-programmers-scala-edition.pdf` — [hmemcpy/milewski-ctfp-pdf](https://github.com/hmemcpy/milewski-ctfp-pdf/releases) |
| Brendan Fong and David Spivak, *Seven Sketches in Compositionality*, 2018 | open, CC BY 4.0 | `open/fong-spivak-2018-seven-sketches-in-compositionality.pdf` — [arXiv:1803.05316](https://arxiv.org/abs/1803.05316) |
| Emily Riehl, *Category Theory in Context*, 2016 | local | `local/riehl-2016-category-theory-in-context.pdf` — [author's site](https://emilyriehl.github.io/files/context.pdf) |
| Dave Gurnell, *The Type Astronaut's Guide to Shapeless*, 2017 (Scala 2) | link | [underscoreio/shapeless-guide](https://github.com/underscoreio/shapeless-guide) |
| Paul Chiusano and Rúnar Bjarnason, *Functional Programming in Scala*, 2nd ed. | paid | Manning |
| Simon Marlow, *Parallel and Concurrent Programming in Haskell*, 2013 | paid | O'Reilly |
| Maurice Herlihy and Nir Shavit, *The Art of Multiprocessor Programming* | paid | Morgan Kaufmann |

## Papers by chapter

A paper used in several chapters is listed in full the first time and by its
file name afterwards.

### Part 0 — Foundations

**Background for the whole course**

- John Hughes, *Why Functional Programming Matters*, 1990 — local:
  `local/hughes-1990-why-functional-programming-matters.pdf`,
  [cs.kent.ac.uk](https://www.cs.kent.ac.uk/people/staff/dat/miranda/whyfp90.pdf).
  Copyright BCS, which allows copies for educational purposes without fee,
  with its notice intact; kept local to stay on the safe side.
- Olivier Blanvillain, Jonathan Brachthäuser, Maxime Kjaer and Martin Odersky,
  *Type-Level Programming with Match Types*, POPL 2022, technical report with
  appendices — open, CC BY:
  `open/blanvillain-et-al-2022-type-level-programming-with-match-types.pdf`,
  [Infoscience](https://infoscience.epfl.ch/record/290019).

**Chapter 01 — Typeclasses and laws**

- Philip Wadler and Stephen Blott, *How to make ad-hoc polymorphism less ad
  hoc*, POPL 1989 — local, converted from the author's PostScript:
  `local/wadler-blott-1989-ad-hoc-polymorphism-less-ad-hoc.pdf`,
  [homepages.inf.ed.ac.uk](https://homepages.inf.ed.ac.uk/wadler/papers/class/class.ps).
- Philip Wadler, *Theorems for free!*, FPCA 1989 — local, converted from
  PostScript: `local/wadler-1989-theorems-for-free.pdf`,
  [homepages.inf.ed.ac.uk](https://homepages.inf.ed.ac.uk/wadler/papers/free/free.ps).

**Chapter 02 — Applicative, Monad and Traverse**

- Conor McBride and Ross Paterson, *Applicative programming with effects*, JFP
  2008 — local: `local/mcbride-paterson-2008-applicative-programming-with-effects.pdf`,
  [staff.city.ac.uk](https://www.staff.city.ac.uk/~ross/papers/Applicative.pdf).
- Philip Wadler, *Monads for functional programming*, 1995 — local:
  `local/wadler-1995-monads-for-functional-programming.pdf`,
  [homepages.inf.ed.ac.uk](https://homepages.inf.ed.ac.uk/wadler/papers/marktoberdorf/baastad.pdf).
- Jeremy Gibbons and Bruno Oliveira, *The essence of the Iterator pattern*, JFP
  2009 — local: `local/gibbons-oliveira-2009-essence-of-the-iterator-pattern.pdf`,
  [cs.ox.ac.uk](https://www.cs.ox.ac.uk/jeremy.gibbons/publications/iterator.pdf).
- Mauro Jaskelioff and Ondřej Rypáček, *An investigation of the laws of
  traversals*, MSFP 2012 — local (the arXiv copy carries arXiv's
  non-exclusive licence): `local/jaskelioff-rypacek-2012-laws-of-traversals.pdf`,
  [arXiv:1202.2919](https://arxiv.org/abs/1202.2919).

**Chapter 03 — State, Reader, Writer and Validated**

- Wadler 1995 and McBride–Paterson 2008, above.
- Jeremy Gibbons and Ralf Hinze, *Just do it: simple monadic equational
  reasoning*, ICFP 2011 — local: `local/gibbons-hinze-2011-just-do-it.pdf`,
  [cs.ox.ac.uk](https://www.cs.ox.ac.uk/jeremy.gibbons/publications/mr.pdf).

**Chapter 04 — Eval and trampolining**

- Rúnar Óli Bjarnason, *Stackless Scala With Free Monads*, 2012 — local:
  `local/bjarnason-2012-stackless-scala-with-free-monads.pdf`,
  [blog.higher-order.com](https://blog.higher-order.com/assets/trampolines.pdf).
- Steven E. Ganz, Daniel P. Friedman and Mitchell Wand, *Trampolined Style*,
  ICFP 1999 — missing: no free copy found.

**Chapter 05 — IO as a description**

- Simon Peyton Jones, *Tackling the Awkward Squad*, 2001 — local:
  `local/peyton-jones-2001-tackling-the-awkward-squad.pdf`,
  [Microsoft Research](https://www.microsoft.com/en-us/research/wp-content/uploads/2016/07/mark.pdf).
- Philip Wadler, *How to Declare an Imperative*, ACM Computing Surveys 1997 —
  local, converted from PostScript:
  `local/wadler-1997-how-to-declare-an-imperative.pdf`,
  [homepages.inf.ed.ac.uk](https://homepages.inf.ed.ac.uk/wadler/papers/monadsdeclare/monadsdeclare.ps).
- *Functional Programming in Scala*, chapter 13 — paid, see Books.

**Chapter 06 — Fibers and schedulers**

- Simon Marlow, Simon Peyton Jones, Andrew Moran and John Reppy, *Asynchronous
  Exceptions in Haskell*, PLDI 2001 — local:
  `local/marlow-et-al-2001-asynchronous-exceptions-in-haskell.pdf`,
  [simonmar.github.io](https://simonmar.github.io/bib/papers/async.pdf).
- JEP 444, *Virtual Threads* — link: [openjdk.org](https://openjdk.org/jeps/444).

**Chapter 07 — Concurrent primitives**

- Marlow et al. 2001, above; Herlihy and Shavit and Marlow 2013, see Books.

**Chapter 08 — Resource and Stream**

- No paper yet.

### Part I — Beyond the monad

**Chapter 09 — Static and dynamic structure**

- Andrey Mokhov, Neil Mitchell and Simon Peyton Jones, *Build Systems à la
  Carte*, ICFP 2018 — local:
  `local/mokhov-mitchell-peyton-jones-2018-build-systems-a-la-carte.pdf`,
  [Microsoft Research](https://www.microsoft.com/en-us/research/uploads/prod/2018/03/build-systems.pdf).
  Applicative versus monadic tasks: what a build system can know before it runs.

**Chapter 10 — Selective functors**

- Andrey Mokhov, Georgy Lukyanov, Simon Marlow and Jeremie Dimino, *Selective
  Applicative Functors*, ICFP 2019, anonymised submitted version — local:
  `local/mokhov-et-al-2019-selective-applicative-functors.pdf`,
  [snowleopard/selective](https://github.com/snowleopard/selective/releases/tag/icfp-submission).
  The published version is CC BY 4.0 in the ACM Digital Library
  ([doi:10.1145/3341694](https://doi.org/10.1145/3341694)), which blocks
  automated downloads; replacing this copy with it would let the file move to
  `open/`.
- Build Systems à la Carte, above.

**Chapter 11 — Free constructions**

- Paolo Capriotti and Ambrus Kaposi, *Free Applicative Functors*, MSFP 2014 —
  open, CC BY: `open/capriotti-kaposi-2014-free-applicative-functors.pdf`,
  [arXiv:1403.0749](https://arxiv.org/abs/1403.0749).
- Wouter Swierstra, *Data types à la carte*, JFP 2008 — local:
  `local/swierstra-2008-data-types-a-la-carte.pdf`,
  [webspace.science.uu.nl](https://webspace.science.uu.nl/~swier004/publications/2008-jfp.pdf).
- Oleg Kiselyov and Hiromi Ishii, *Freer Monads, More Extensible Effects*,
  Haskell 2015 — local:
  `local/kiselyov-ishii-2015-freer-monads-more-extensible-effects.pdf`,
  [okmij.org](https://okmij.org/ftp/Haskell/extensible/more.pdf).
- Phil Freeman, *Stack Safety for Free*, 2015 — local:
  `local/freeman-2015-stack-safety-for-free.pdf`,
  [functorial.com](https://functorial.com/stack-safety-for-free/index.pdf).
- Bjarnason 2012, above.

**Chapter 12 — Yoneda, Coyoneda and Codensity**

- Janis Voigtländer, *Asymptotic Improvement of Computations over Free
  Monads*, MPC 2008 — local:
  `local/voigtlaender-2008-asymptotic-improvement-over-free-monads.pdf`,
  [janis-voigtlaender.eu](https://www.janis-voigtlaender.eu/papers/AsymptoticImprovementOfComputationsOverFreeMonads.pdf).
- Atze van der Ploeg and Oleg Kiselyov, *Reflection without Remorse*, Haskell
  2014 — local: `local/van-der-ploeg-kiselyov-2014-reflection-without-remorse.pdf`,
  [okmij.org](https://okmij.org/ftp/Haskell/zseq.pdf).

> **M3 — the DSL**
>
> - Jeremy Gibbons and Nicolas Wu, *Folding Domain-Specific Languages: Deep and
>   Shallow Embeddings*, ICFP 2014 — local:
>   `local/gibbons-wu-2014-folding-domain-specific-languages.pdf`,
>   [cs.ox.ac.uk](https://www.cs.ox.ac.uk/jeremy.gibbons/publications/embedding.pdf).
> - Selective Applicative Functors and Free Applicative Functors, above.

### Part II — Effects as interfaces

**Chapter 13 — Tagless final in depth**

- Jacques Carette, Oleg Kiselyov and Chung-chieh Shan, *Finally Tagless,
  Partially Evaluated*, JFP 2009 — local:
  `local/carette-kiselyov-shan-2009-finally-tagless-partially-evaluated.pdf`,
  [okmij.org](https://okmij.org/ftp/tagless-final/JFP.pdf).
- Oleg Kiselyov, *Typed Tagless Final Interpreters*, lecture notes, 2012 —
  local: `local/kiselyov-2012-typed-tagless-final-interpreters.pdf`,
  [okmij.org](https://okmij.org/ftp/tagless-final/course/lecture.pdf).

**Chapter 14 — mtl, Kleisli and transformers**

- Martin Grabmüller, *Monad Transformers Step by Step*, 2006 — open, BSD
  3-clause (Copyright (c) 2014, Martin Grabmueller):
  `open/grabmueller-2006-monad-transformers-step-by-step.pdf`,
  [mgrabmueller/TransformersStepByStep](https://github.com/mgrabmueller/TransformersStepByStep).
- Sheng Liang, Paul Hudak and Mark Jones, *Monad Transformers and Modular
  Interpreters*, POPL 1995 — missing: the author's PostScript link returns 404.

**Chapter 15 — Algebraic effects and direct style**

- Gordon Plotkin and Matija Pretnar, *Handling Algebraic Effects*, LMCS 2013 —
  open, CC BY-ND 2.0:
  `open/plotkin-pretnar-2013-handling-algebraic-effects.pdf`,
  [arXiv:1312.1399](https://arxiv.org/abs/1312.1399).
- Matija Pretnar, *An Introduction to Algebraic Effects and Handlers*, MFPS
  2015 — local:
  `local/pretnar-2015-introduction-to-algebraic-effects-and-handlers.pdf`,
  [eff-lang.org](https://www.eff-lang.org/handlers-tutorial.pdf).
- Martin Odersky, Aleksander Boruch-Gruszecki, Edward Lee, Jonathan
  Brachthäuser and Ondřej Lhoták, *Scoped Capabilities for Polymorphic Effects*, 2022 —
  local: `local/odersky-et-al-2022-scoped-capabilities.pdf`,
  [arXiv:2207.03402](https://arxiv.org/abs/2207.03402). Capture checking.

### Part III — Recursion and optics

**Chapter 16 — Recursion schemes I**

- Erik Meijer, Maarten Fokkinga and Ross Paterson, *Functional Programming
  with Bananas, Lenses, Envelopes and Barbed Wire*, FPCA 1991 — local:
  `local/meijer-fokkinga-paterson-1991-bananas-lenses-envelopes-barbed-wire.pdf`,
  [maartenfokkinga.github.io](https://maartenfokkinga.github.io/utwente/mmf91m.pdf).

**Chapter 17 — Recursion schemes II**

- Ralf Hinze, Nicolas Wu and Jeremy Gibbons, *Unifying Structured Recursion
  Schemes*, ICFP 2013 — local:
  `local/hinze-wu-gibbons-2013-unifying-structured-recursion-schemes.pdf`,
  [cs.ox.ac.uk](https://www.cs.ox.ac.uk/jeremy.gibbons/publications/urs.pdf).

**Chapter 18 — Comonads and zippers**

- Gérard Huet, *The Zipper*, JFP 1997 — local: `local/huet-1997-the-zipper.pdf`,
  [gallium.inria.fr](https://gallium.inria.fr/~huet/PUBLIC/zip.pdf).
- Tarmo Uustalu and Varmo Vene, *Comonadic Notions of Computation*, CMCS 2008 —
  missing: [cs.ioc.ee](https://cs.ioc.ee/~tarmo/papers/cmcs08.pdf) did not
  answer when this list was assembled.

**Chapter 19 — Profunctor optics**

- Matthew Pickering, Jeremy Gibbons and Nicolas Wu, *Profunctor Optics: Modular
  Data Accessors*, The Art, Science, and Engineering of Programming 2017 —
  open, CC BY 4.0: `open/pickering-gibbons-wu-2017-profunctor-optics.pdf`,
  [arXiv:1703.10857](https://arxiv.org/abs/1703.10857).
- Bryce Clarke, Derek Elkins, Jeremy Gibbons, Fosco Loregian, Bartosz
  Milewski, Emily Pillmore and Mario Román, *Profunctor Optics, a Categorical
  Update*, Compositionality 2024 — open, CC BY 4.0:
  `open/clarke-et-al-2020-profunctor-optics-categorical-update.pdf`,
  [arXiv:2001.07488](https://arxiv.org/abs/2001.07488).

> **M5 — the planner**
>
> - Andrey Mokhov, *Algebraic Graphs with Class*, Haskell 2017 — local:
>   `local/mokhov-2017-algebraic-graphs-with-class.pdf`,
>   [snowleopard/alga-paper](https://github.com/snowleopard/alga-paper/releases/tag/final).
> - Unifying Structured Recursion Schemes, above.

### Part IV — Categories that pay rent

**Chapter 20 — Natural transformations and adjunctions**

- Milewski, Riehl, and Fong and Spivak, see Books.

**Chapter 21 — Category, Arrow and profunctors**

- John Hughes, *Generalising Monads to Arrows*, Science of Computer
  Programming 2000 — local: `local/hughes-2000-generalising-monads-to-arrows.pdf`,
  [cse.chalmers.se](https://www.cse.chalmers.se/~rjmh/Papers/arrows.pdf).
