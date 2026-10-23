package advfp.ch06fibersandschedulers

import java.util.concurrent.{ExecutorService, Executors}

/** Exercise 01 — Write two schedulers behind one interface: a pool of OS
  * threads, and virtual threads. *Solved.*
  *
  * Both are an `ExecutorService` behind `execute`, and the work is in choosing
  * which one. `newFixedThreadPool(n)` keeps `n` OS threads and a queue: a task
  * waits in the queue until one of the `n` is free, so at most `n` run at once,
  * and a task that blocks keeps its thread from every task behind it. That is
  * the deadlock of the `pool(1)` test — the task that would release the
  * waiting one is in the queue behind it — and it is a property of the
  * strategy, not a bug in it: a pool is a promise to use at most `n` threads.
  *
  * `newVirtualThreadPerTaskExecutor()` starts a virtual thread per task and
  * queues nothing. A virtual thread is scheduled by the JVM onto a few OS
  * "carrier" threads; when it blocks on a latch, a sleep or a socket, it is
  * unmounted and its carrier runs another. Ten thousand of them blocked at once
  * cost ten thousand small heap objects. A pool cannot pass that test at any
  * size worth having, and the virtual scheduler cannot pass "never four at
  * once" — the two tests are the difference between the strategies, written
  * down.
  *
  * The pool's threads are daemons, from `Thread.ofPlatform().daemon(true)`: a
  * pool left running by a program that forgot to shut it down must not keep
  * the JVM alive. Virtual threads are always daemons. `require` sits in `pool`
  * itself, so that a bad size fails where it is passed and not when the first
  * task is executed.
  *
  * `shutdownNow` rather than `shutdown`: the second waits for the tasks already
  * running, and a fiber that never ends would keep its thread forever.
  */
object Exercise01:

  trait Scheduler:
    def execute(task: Runnable): Unit
    def shutdown(): Unit

  def pool(threads: Int): Scheduler =
    require(threads >= 1, s"a pool needs at least one thread, got $threads")
    from(Executors.newFixedThreadPool(threads, Thread.ofPlatform().daemon(true).factory()))

  def virtual(): Scheduler = from(Executors.newVirtualThreadPerTaskExecutor())

  private def from(executor: ExecutorService): Scheduler =
    new Scheduler:
      def execute(task: Runnable): Unit = executor.execute(task)

      def shutdown(): Unit =
        val _ = executor.shutdownNow()
end Exercise01
