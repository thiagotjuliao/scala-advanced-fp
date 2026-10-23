package advfp.ch06fibersandschedulers

import advfp.core.unsolved

/** Exercise 01 — Write two schedulers behind one interface: a pool of OS
  * threads, and virtual threads.
  *
  * A `Scheduler` runs a task on some thread, later, and returns at once.
  * Write two:
  *
  *   - `pool(threads)`: a fixed pool of `threads` OS threads. At most `threads`
  *     tasks run at once; the others wait their turn. Fewer than one thread is
  *     rejected with `require`, when `pool` is called.
  *   - `virtual()`: a virtual thread for every task.
  *
  * Both keep the same contract: every task runs exactly once, on a thread
  * other than the caller's, and two tasks can run at the same time. `shutdown`
  * stops the threads, including those running a task that never ends.
  *
  * The `Scheduler` in `IO.scala` is the answer; solve this one before reading
  * it.
  *
  * Hint: `java.util.concurrent.Executors` has a factory for each, and
  * `java.lang.Thread.ofPlatform()` builds the threads a pool should use —
  * should a pool someone forgot to shut down keep the JVM from exiting?
  */
object Exercise01:

  trait Scheduler:
    def execute(task: Runnable): Unit
    def shutdown(): Unit

  def pool(threads: Int): Scheduler = unsolved

  def virtual(): Scheduler = unsolved
end Exercise01
