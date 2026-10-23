package advfp.ch06fibersandschedulers

import advfp.core.CourseSuite

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}

import Exercise01.*

class Exercise01Spec extends CourseSuite:

  // Each test makes its own scheduler and shuts it down, so a test that fails
  // leaves no threads behind for the next.
  private def withScheduler[A](make: => Scheduler)(body: Scheduler => A): A =
    val scheduler = make
    try body(scheduler)
    finally scheduler.shutdown()

  // Long enough for any machine, and only ever waited out when the claim fails.
  private def await(latch: CountDownLatch): Boolean = latch.await(5, TimeUnit.SECONDS)

  // The interface promises the same thing whatever is behind it; the contract
  // is checked against both.
  private val both = List("pool(4)" -> (() => pool(4)), "virtual" -> (() => virtual()))

  // --- the contract ------------------------------------------------------------

  for (name, make) <- both do
    test(s"$name runs a task, on another thread") {
      withScheduler(make()): scheduler =>
        val caller = Thread.currentThread
        val ranElsewhere = AtomicBoolean(false)
        val done = CountDownLatch(1)
        scheduler.execute: () =>
          ranElsewhere.set(Thread.currentThread ne caller)
          done.countDown()
        assert(await(done), "the task never ran")
        assert(ranElsewhere.get, "the task ran on the caller's thread")
    }

    test(s"$name runs every task exactly once") {
      withScheduler(make()): scheduler =>
        val runs = AtomicInteger(0)
        val done = CountDownLatch(1000)
        (1 to 1000).foreach: _ =>
          scheduler.execute: () =>
            val _ = runs.incrementAndGet()
            done.countDown()
        assert(await(done), s"only ${1000 - done.getCount} of 1000 tasks ran")
        assertEquals(runs.get, 1000)
    }

    test(s"$name runs two tasks at the same time") {
      // Each waits for the other: run one after the other, the first one waits
      // in vain.
      withScheduler(make()): scheduler =>
        val first = CountDownLatch(1)
        val second = CountDownLatch(1)
        val met = CountDownLatch(2)
        scheduler.execute: () =>
          first.countDown()
          if await(second) then met.countDown()
        scheduler.execute: () =>
          second.countDown()
          if await(first) then met.countDown()
        assert(await(met), "the two tasks did not run at the same time")
    }
  end for

  // --- the pool ----------------------------------------------------------------

  test("pool runs its tasks on OS threads") {
    withScheduler(pool(2)): scheduler =>
      val virtualThread = AtomicBoolean(true)
      val done = CountDownLatch(1)
      scheduler.execute: () =>
        virtualThread.set(Thread.currentThread.isVirtual)
        done.countDown()
      assert(await(done))
      assert(!virtualThread.get, "the task ran on a virtual thread")
  }

  test("pool(3) runs three tasks at once, and never four") {
    withScheduler(pool(3)): scheduler =>
      val running = AtomicInteger(0)
      val most = AtomicInteger(0)
      val done = CountDownLatch(12)
      (1 to 12).foreach: _ =>
        scheduler.execute: () =>
          val now = running.incrementAndGet()
          val _ = most.accumulateAndGet(now, math.max)
          Thread.sleep(30)
          val _ = running.decrementAndGet()
          done.countDown()
      assert(await(done))
      assertEquals(most.get, 3)
  }

  test("pool(1) cannot run a task that another task blocks waiting for") {
    // The first task holds the only thread while it waits; the second, which
    // would release it, is queued behind it.
    withScheduler(pool(1)): scheduler =>
      val released = CountDownLatch(1)
      val sawRelease = AtomicBoolean(true)
      val done = CountDownLatch(1)
      scheduler.execute: () =>
        sawRelease.set(released.await(200, TimeUnit.MILLISECONDS))
        done.countDown()
      scheduler.execute(() => released.countDown())
      assert(await(done))
      assert(!sawRelease.get, "the waiting task saw a task queued behind it run")
  }

  test("pool rejects fewer than one thread, when it is called") {
    intercept[IllegalArgumentException](pool(0))
  }

  // --- virtual threads ---------------------------------------------------------

  test("virtual runs its tasks on virtual threads") {
    withScheduler(virtual()): scheduler =>
      val virtualThread = AtomicBoolean(false)
      val done = CountDownLatch(1)
      scheduler.execute: () =>
        virtualThread.set(Thread.currentThread.isVirtual)
        done.countDown()
      assert(await(done))
      assert(virtualThread.get, "the task ran on an OS thread")
  }

  test("virtual keeps ten thousand blocked tasks waiting at once") {
    // Each task arrives and then blocks until all have arrived. A pool of any
    // size this machine can afford never gets them all there.
    withScheduler(virtual()): scheduler =>
      val tasks = 10_000
      val arrived = CountDownLatch(tasks)
      val gate = CountDownLatch(1)
      // Waited for before the scheduler shuts down, which would interrupt the
      // tasks still on their way out of the gate.
      val left = CountDownLatch(tasks)
      (1 to tasks).foreach: _ =>
        scheduler.execute: () =>
          arrived.countDown()
          val _ = gate.await(5, TimeUnit.SECONDS)
          left.countDown()
      val allThere = await(arrived)
      gate.countDown()
      val _ = await(left)
      assert(allThere, s"${arrived.getCount} of $tasks tasks never got to run")
  }
end Exercise01Spec
