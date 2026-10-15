package advfp.ch01typeclassesandlaws

import advfp.core.CourseSuite

import Exercise04.*

class Exercise04Spec extends CourseSuite:

  private def keepsIdentity(xs: List[Int]): Boolean = reversing.map(xs)(identity) == xs

  test("the counterexample breaks the identity law") {
    assert(!keepsIdentity(counterexample), s"reversing keeps $counterexample as it was")
  }

  test("the counterexample is as short as one can be") {
    assertEquals(counterexample.size, 2)
  }

  test("every blind spot satisfies the identity law") {
    forAllSamples(blindSpots): xs =>
      assert(keepsIdentity(xs), s"reversing changes $xs, so it is not a blind spot")
  }

  test("there are at least three different blind spots") {
    assert(blindSpots.distinct.size >= 3, s"only ${blindSpots.distinct.size} distinct")
  }

  test("at least one blind spot is long enough not to be an accident of size") {
    assert(blindSpots.exists(_.size >= 3), "every blind spot has fewer than three elements")
  }
