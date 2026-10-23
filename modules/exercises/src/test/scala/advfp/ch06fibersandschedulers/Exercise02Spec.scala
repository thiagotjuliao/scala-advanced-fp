package advfp.ch06fibersandschedulers

import advfp.core.CourseSuite

import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicReference}

import Exercise02.*
import Exercise02.IO.*

class Exercise02Spec extends CourseSuite:

  private val deep = 1_000_000

  // What a run did, and whether it has been asked to stop: one per test. `run`
  // hands the loop a way to read the flag; an effect sets it, as another fiber
  // would.
  private final class Probe:
    private val lines = AtomicReference(Vector.empty[String])
    private val flag = AtomicBoolean(false)

    def note(line: String): Unit =
      val _ = lines.updateAndGet(_ :+ line)

    def requestCancel(): Unit = flag.set(true)

    def write(line: String): IO[Unit] = Delay(() => note(line))

    def cancel: IO[Unit] = Delay(() => requestCancel())

    def written: Vector[String] = lines.get

    def run[A](io: IO[A]): Outcome[A] = io.run(() => flag.get)

  private final class Boom extends RuntimeException("boom")

  private val boom = Boom()

  // --- without cancellation ----------------------------------------------------

  test("a program that is not canceled succeeds, with its value and its effects") {
    val probe = Probe()
    val io = probe.write("a").flatMap(_ => probe.write("b")).map(_ => 1)
    assertEquals(probe.run(io), Outcome.Succeeded(1))
    assertEquals(probe.written, Vector("a", "b"))
  }

  test("a program that fails is Errored") {
    val probe = Probe()
    assertEquals(probe.run(RaiseError(boom)), Outcome.Errored(boom))
    assertEquals(probe.run(Delay[Int](() => throw boom).map(_ + 1)), Outcome.Errored(boom))
  }

  test("a handler recovers from an error when nothing is canceled") {
    val probe = Probe()
    val io = (RaiseError(boom): IO[Int]).handleErrorWith(_ => probe.write("handled").map(_ => 2))
    assertEquals(probe.run(io), Outcome.Succeeded(2))
    assertEquals(probe.written, Vector("handled"))
  }

  // --- cancellation points -----------------------------------------------------

  test("a cancel stops the program at the next bind") {
    val probe = Probe()
    val io = probe
      .write("1")
      .flatMap(_ => probe.cancel)
      .flatMap(_ => probe.write("2"))
      .flatMap(_ => probe.write("3"))
    assertEquals(probe.run(io), Outcome.Canceled)
    assertEquals(probe.written, Vector("1"))
  }

  test("map is a bind, and so a cancellation point") {
    val probe = Probe()
    assertEquals(probe.run(probe.cancel.map(_ => 1)), Outcome.Canceled)
  }

  test("the effect that cancels is not interrupted: it runs to its end") {
    val probe = Probe()
    val io = Delay { () =>
      probe.requestCancel()
      probe.note("the rest of the effect")
    }.flatMap(_ => probe.write("next"))
    assertEquals(probe.run(io), Outcome.Canceled)
    assertEquals(probe.written, Vector("the rest of the effect"))
  }

  test("a canceled program runs no handler") {
    val probe = Probe()
    val io = Delay[Int] { () =>
      probe.requestCancel()
      throw boom
    }.handleErrorWith(_ => probe.write("handled").map(_ => 0))
    assertEquals(probe.run(io), Outcome.Canceled)
    assertEquals(probe.written, Vector.empty)
  }

  test("the flag is read at every bind, not once at the start") {
    val probe = Probe()
    val io = (1 to 10).foldLeft[IO[Unit]](Pure(())): (acc, i) =>
      acc.flatMap(_ => if i == 4 then probe.cancel else probe.write(s"$i"))
    assertEquals(probe.run(io), Outcome.Canceled)
    assertEquals(probe.written, Vector("1", "2", "3"))
  }

  // --- stack safety ------------------------------------------------------------

  test("a million binds succeed when nothing cancels them") {
    val probe = Probe()
    val io = (1 to deep).foldLeft[IO[Int]](Pure(0))((acc, _) => acc.flatMap(n => Pure(n + 1)))
    assertEquals(probe.run(io), Outcome.Succeeded(deep))
  }

  test("a cancel half a million binds in stops there") {
    val probe = Probe()
    val ticks = AtomicInteger(0)
    val tick: IO[Unit] =
      Delay(() => if ticks.incrementAndGet() == deep / 2 then probe.requestCancel())
    val io = (1 to deep).foldLeft[IO[Unit]](Pure(()))((acc, _) => acc.flatMap(_ => tick))
    assertEquals(probe.run(io), Outcome.Canceled)
    assertEquals(ticks.get, deep / 2)
  }

  test("an error unwinds through a million binds") {
    val probe = Probe()
    val io =
      (1 to deep).foldLeft[IO[Int]](RaiseError(boom))((acc, _) => acc.flatMap(n => Pure(n + 1)))
    assertEquals(probe.run(io), Outcome.Errored(boom))
  }
end Exercise02Spec
