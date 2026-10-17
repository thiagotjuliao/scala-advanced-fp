package advfp.ch03statereaderwriterandvalidated

/** Exercise 02 — Write `Reader` for a configuration and `Writer` for a log.
  *
  * `Reader[R, A]` is a program that needs an environment `R` to produce an `A`.
  *
  *   - `ask`: the environment itself, as the value.
  *   - `local`: run a program in an environment changed by `f`. Only that
  *     program sees the change; what is sequenced after it sees the original.
  *   - `readerMonad`: `pure` and `flatMap`, both steps reading the same
  *     environment.
  *   - `baseUrl`: `"https://host:port"` when the configuration is secure,
  *     `"http://host:port"` otherwise. `endpoint(path)`: the base URL followed
  *     by `path`. `onStaging(program)`: `program` run against the host with
  *     `"staging."` in front of it.
  *
  * `Writer[W, A]` is a value together with a log of how it was produced.
  *
  *   - `tell`: log `w`, and return nothing.
  *   - `writerMonad`: `pure` and `flatMap`, for any log with a `Monoid`. The
  *     log of the first step comes before the log of the second.
  *   - `collatz(n)`: the number of steps from `n` to 1 — halve an even number,
  *     take `3n + 1` of an odd one — logging every value visited, `n` and 1
  *     included. `n` must be positive.
  *
  * Write `endpoint`, `onStaging` and `collatz` with the operations above, not
  * by building the case classes by hand: the point is that the environment
  * and the log are never mentioned.
  *
  * Hint: in `collatz`, each step is "log the current value, then the steps of
  * the next one, plus one".
  */
object Exercise02:

  final case class Reader[R, A](run: R => A)

  object Reader:
    def ask[R]: Reader[R, R] = Reader(identity)

    def local[R, A](f: R => R)(fa: Reader[R, A]): Reader[R, A] =
      Reader: r =>
        fa.run(f(r))

  given readerMonad: [R] => Monad[[A] =>> Reader[R, A]] =
    new Monad[[A] =>> Reader[R, A]]:
      def pure[A](a: A): Reader[R, A] = Reader(_ => a)

      extension [A](fa: Reader[R, A])
        override def flatMap[B](f: A => Reader[R, B]): Reader[R, B] =
          Reader: r =>
            f(fa.run(r)).run(r)

      extension [A](fa: Reader[R, A])
        override def map[B](f: A => B): Reader[R, B] =
          Reader: r =>
            f(fa.run(r))

      extension [A, B](ff: Reader[R, A => B])
        override def ap(fa: Reader[R, A]): Reader[R, B] =
          Reader: r =>
            val f = ff.run(r)
            val a = fa.run(r)
            f(a)

  final case class Config(host: String, port: Int, secure: Boolean)

  def baseUrl: Reader[Config, String] =
    Reader:
      case Config(host, port, false) => s"http://$host:$port"
      case Config(host, port, true) => s"https://$host:$port"

  def endpoint(path: String): Reader[Config, String] =
    baseUrl.map(_ + path)

  def onStaging[A](program: Reader[Config, A]): Reader[Config, A] =
    Reader.local[Config, A](cfg => cfg.copy(host = s"staging.${cfg.host}"))(program)

  final case class Writer[W, A](log: W, value: A)

  object Writer:
    def tell[W](w: W): Writer[W, Unit] = Writer(w, ())

  given writerMonad: [W: Monoid] => Monad[[A] =>> Writer[W, A]] =
    new Monad[[A] =>> Writer[W, A]]:
      def pure[A](a: A): Writer[W, A] = Writer(Monoid[W].empty, a)

      extension [A](fa: Writer[W, A])
        override def flatMap[B](f: A => Writer[W, B]): Writer[W, B] =
          val Writer(logA, a) = fa
          val Writer(logB, b) = f(a)
          Writer(logA |+| logB, b)

      extension [A](fa: Writer[W, A])
        override def map[B](f: A => B): Writer[W, B] =
          val Writer(log, a) = fa
          Writer(log, f(a))

      extension [A, B](ff: Writer[W, A => B])
        override def ap(fa: Writer[W, A]): Writer[W, B] =
          val Writer(logA, a) = fa
          val Writer(logF, f) = ff
          Writer(logA |+| logF, f(a))

  def collatz(n: Int): Writer[List[Int], Int] =
    require(n > 0, "n must be positive")

    if n == 1 then Writer(List(1), 0)
    else
      /** for
        *  _     <- Writer.tell(List(n))
        *  steps <- if n % 2 == 0 then collatz(n / 2) else collatz(3 * n + 1)
        * yield steps + 1
        */
      Writer
        .tell(List(n))
        .map(_ => 1)
        .flatMap: steps =>
          val next =
            if n % 2 == 0 then collatz(n / 2)
            else collatz(3 * n + 1)
          next.map(_ + steps)
end Exercise02
