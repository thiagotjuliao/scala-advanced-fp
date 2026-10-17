package advfp.ch04evalandtrampolining

import advfp.core.CourseSuite

import Exercise03.*

class Exercise03Spec extends CourseSuite:

  private val long: List[Int] = List.range(0, 1_000_000)

  // A function that fails the test if it is ever called on an element past
  // `limit`: how a spec sees that a fold stopped, without a counter.
  private def upTo[A](limit: Int)(f: Int => A): Int => A =
    n => if n > limit then fail(s"looked at $n, past $limit") else f(n)

  test("foldRight of the empty list is the initial value") {
    assertEquals(foldRight(Nil: List[Int], Eval.now("z"))((_, acc) => acc).value, "z")
  }

  test("foldRight combines from the right") {
    val folded = foldRight(List(1, 2, 3), Eval.now(List.empty[Int]))((a, acc) => acc.map(a :: _))
    assertEquals(folded.value, List(1, 2, 3))
  }

  test("foldRight is not left-to-right in disguise") {
    // Subtraction does not commute: 1 - (2 - (3 - 0)) = 2, ((0 - 1) - 2) - 3 = -6.
    assertEquals(foldRight(List(1, 2, 3), Eval.now(0))((a, acc) => acc.map(a - _)).value, 2)
  }

  test("foldRight calls the function on the first element first") {
    val check = upTo(1)(identity)
    val folded = foldRight(List(1, 2, 3, 4), Eval.now(0)) { (a, acc) =>
      if check(a) == 1 then Eval.now(-1) else acc
    }
    assertEquals(folded.value, -1)
  }

  test("foldRight does not build the rest until the function asks for it") {
    val check = upTo(2)(identity)
    val folded = foldRight(List(1, 2, 3, 4), Eval.now(0)) { (a, acc) =>
      if check(a) == 2 then Eval.now(a) else acc.map(_ + a)
    }
    assertEquals(folded.value, 3)
  }

  test("foldRight over a million elements does not overflow") {
    val folded = foldRight(long, Eval.now(0L))((a, acc) => acc.map(_ + a))
    assertEquals(folded.value, long.map(_.toLong).sum)
  }

  // --- exists ------------------------------------------------------------------

  test("nothing exists in the empty list") {
    assertEquals(exists(Nil: List[Int])(_ => true).value, false)
  }

  test("exists finds an element that matches") {
    assertEquals(exists(List(1, 2, 3))(_ == 3).value, true)
    assertEquals(exists(List(1, 2, 3))(_ == 4).value, false)
  }

  test("exists stops at the first match") {
    assertEquals(exists(List(1, 2, 3, 4))(upTo(2)(_ == 2)).value, true)
  }

  test("exists on a million elements does not overflow") {
    val last = long.last // once: `List.last` walks the whole list
    assertEquals(exists(long)(_ == last).value, true)
    assertEquals(exists(long)(_ < 0).value, false)
  }
end Exercise03Spec
