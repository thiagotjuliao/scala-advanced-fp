package advfp.ch03statereaderwriterandvalidated

/** Exercise 02 — Write `Reader` for a configuration and `Writer` for a log.
  * *Solved.*
  *
  * `Reader`'s `flatMap` runs both steps on the same `r`: the environment is
  * read, never written, so there is nothing to thread. `local` builds a new
  * reader that changes `r` before handing it to `fa` — the change lives inside
  * that one function call, and the next step of a `flatMap` gets the `r` the
  * outer reader was given. That is the whole of "does not leak", and the spec
  * checks it by reading the port after a `local`.
  *
  * `endpoint` and `onStaging` never mention a `Config` value: `endpoint` maps
  * over `baseUrl`, and `onStaging` is `local` with a function on the
  * configuration. A program written this way can be run against any
  * configuration, and a sub-program can be run against a different one,
  * without changing a signature.
  *
  * `Writer`'s `flatMap` combines `fa.log |+| next.log`. The other order has the
  * same type, passes both identity laws — one side is always `empty` there —
  * and writes the log backwards; only the test with two non-empty logs sees
  * it. `pure` logs `Monoid[W].empty`, which is what makes it an identity.
  *
  * `collatz` logs `n` with `tell`, then adds one to the steps of the next
  * value. Recursion depth is the length of the sequence — 111 for 27 — which
  * is nowhere near the stack's limit; a log of millions of entries would need
  * chapter 04.
  */
object Exercise02:

  final case class Reader[R, A](run: R => A)

  object Reader:
    def ask[R]: Reader[R, R] = Reader(r => r)

    def local[R, A](f: R => R)(fa: Reader[R, A]): Reader[R, A] = Reader(r => fa.run(f(r)))

  given readerMonad: [R] => Monad[[A] =>> Reader[R, A]]:
    def pure[A](a: A): Reader[R, A] = Reader(_ => a)

    extension [A](fa: Reader[R, A])
      def flatMap[B](f: A => Reader[R, B]): Reader[R, B] = Reader(r => f(fa.run(r)).run(r))

  final case class Config(host: String, port: Int, secure: Boolean)

  def baseUrl: Reader[Config, String] =
    Reader.ask[Config].map { c =>
      val scheme = if c.secure then "https" else "http"
      s"$scheme://${c.host}:${c.port}"
    }

  def endpoint(path: String): Reader[Config, String] = baseUrl.map(_ + path)

  def onStaging[A](program: Reader[Config, A]): Reader[Config, A] =
    Reader.local[Config, A](c => c.copy(host = s"staging.${c.host}"))(program)

  final case class Writer[W, A](log: W, value: A)

  object Writer:
    def tell[W](w: W): Writer[W, Unit] = Writer(w, ())

  given writerMonad: [W: Monoid] => Monad[[A] =>> Writer[W, A]]:
    def pure[A](a: A): Writer[W, A] = Writer(Monoid[W].empty, a)

    extension [A](fa: Writer[W, A])
      def flatMap[B](f: A => Writer[W, B]): Writer[W, B] =
        val next = f(fa.value)
        Writer(fa.log |+| next.log, next.value)

  def collatz(n: Int): Writer[List[Int], Int] =
    require(n > 0, s"collatz is defined for positive numbers, got $n")
    Writer.tell(List(n)).flatMap { _ =>
      if n == 1 then writerMonad[List[Int]].pure(0)
      else collatz(if n % 2 == 0 then n / 2 else 3 * n + 1).map(_ + 1)
    }
end Exercise02
