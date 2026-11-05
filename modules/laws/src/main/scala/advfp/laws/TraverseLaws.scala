package advfp.laws

import advfp.typeclasses.{Eq, Traverse}
import Id.given

private[laws] object TraverseLaws:
  def identity[F[_]: Traverse](fas: Gen[F[Int]], fs: Gen[Fun[Int, Int]])(using
      Eq[F[Int]]
  ): (String, Prop[(F[Int], Fun[Int, Int])]) =
    val gen =
      for
        fa <- fas
        f <- fs
      yield (fa, f)

    "identity" ->
      Prop.forAll(gen): (fa, f) =>
        Traverse[F].traverse[Id, Int, Int](fa)(f(_)) <->
          Traverse[F].map(fa)(f(_))

  def composition[F[_]: Traverse](
      fas: Gen[F[Int]],
      fs: Gen[Fun[Int, Option[Int]]],
      gs: Gen[Fun[Int, List[Int]]]
  )(using Eq[F[Int]]): (String, Prop[(F[Int], Fun[Int, Option[Int]], Fun[Int, List[Int]])]) =
    val gen =
      for
        fa <- fas
        f <- fs
        g <- gs
      yield (fa, f, g)

    "composition" ->
      Prop.forAll(gen): (fa, f, g) =>
        Traverse[F]
          .traverse[[X] =>> Nested[Option, List, X], Int, Int](fa)(a => Nested(f(a).map(g(_))))
          .value
          <->
            Traverse[F].traverse(fa)(f(_)).map(fb => Traverse[F].traverse(fb)(g(_)))

  def naturality[F[_]: Traverse](
      fas: Gen[F[Int]],
      fs: Gen[Fun[Int, Option[Int]]]
  )(using Eq[F[Int]]): (String, Prop[(F[Int], Fun[Int, Option[Int]])]) =
    val gen =
      for
        fa <- fas
        f <- fs
      yield (fa, f)

    "naturality" ->
      Prop.forAll(gen): (fa, f) =>
        Traverse[F].traverse(fa)(f(_)).toList <-> Traverse[F].traverse(fa)(a => f(a).toList)

  def foldLeftAndTraverse[F[_]: Traverse](
      fas: Gen[F[Int]]
  ): (String, Prop[F[Int]]) =
    "foldLeft agrees with traverse" ->
      Prop.forAll(fas): fa =>
        Traverse[F]
          .traverse[[X] =>> Const[List[Int], X], Int, List[Int]](fa)(a => Const(List(a)))
          .value
          <->
            Traverse[F].foldLeft(fa)(List.empty[Int])(_ :+ _)
end TraverseLaws
