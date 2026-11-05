package advfp.laws

import munit.FunSuite

class SizeSpec extends FunSuite:

  test("of keeps the number, from zero to Int.MaxValue"):
    List(0, 1, 30, Int.MaxValue).foreach: n =>
      assertEquals(Size.of(n).toInt, n)

  test("of rejects a negative number, and says which"):
    List(-1, Int.MinValue).foreach: n =>
      val error = intercept[IllegalArgumentException](Size.of(n))
      assert(error.getMessage.contains(n.toString), s"the message misses $n: ${error.getMessage}")

  test("a size is a value: the same number gives the same size"):
    assertEquals(Size.of(3), Size.of(3))
    assertNotEquals(Size.of(3), Size.of(4))

  test("an Int is not a size, and a size is not an Int, without of and toInt"):
    assert(compileErrors("val size: Size = 3").nonEmpty, "an Int passes as a Size")
    assert(compileErrors("val n: Int = Size.of(3)").nonEmpty, "a Size passes as an Int")
    assert(compileErrors("Size.of(3) + 1").nonEmpty, "a Size takes Int arithmetic")
end SizeSpec
