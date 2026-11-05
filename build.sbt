ThisBuild / scalaVersion := "3.9.0"
ThisBuild / organization := "dev.thiagojuliao"
ThisBuild / version := "0.1.0"

lazy val commonSettings = Seq(
  // Tests run from the class directories, not from jars. With sbt 2's default
  // `exportJars := true` the test classpath is the packaged -tests.jar, and
  // when compile is served from the disk cache (sbt 2.0.9) that jar is not
  // repackaged: undoing an edit ran the tests against the bytecode of the last
  // real compile. Measured 2026-10-02 in scala-type-programming.
  exportJars := false,
  scalacOptions ++= CompilerFlags.base,
  libraryDependencies += "org.scalameta" %% "munit" % "1.3.6" % Test,
  testFrameworks += new TestFramework("munit.Framework"),
  // A snippet handed to `assertTypeChecks` is type-checked in the scope of its
  // call site, so the imports it leans on are used — by a string the unused
  // import check never looks inside. The check stays on for main sources.
  //
  // `~=`, not `:= scalacOptions.value...`: the unscoped `scalacOptions` lacks
  // the `-Xsemanticdb` options sbt adds per configuration, and Metals then
  // reports the test targets as misconfigured.
  Test / scalacOptions ~= (_.filterNot(_ == CompilerFlags.unusedImports))
)

// Where a warning is a defect: core, the answer key and the two modules built
// from scratch. Main sources only — test sources keep `base`, since `-Wall`
// turns on the unused-import check that snippet assertions trip by design.
// The Test filter is needed: Test's options start from Compile's, so without
// it the tests inherit `-Werror` too.
lazy val strictSettings = Seq(
  Compile / scalacOptions ++= CompilerFlags.strict,
  Test / scalacOptions ~= (_.filterNot(CompilerFlags.strict.contains))
)

// The snippets of `assertTypeChecks` hide their dependencies from incremental
// compilation: neither zinc nor sbt's cache sees that a spec uses what a string
// names, so an edited exercise left the spec's verdicts untouched. The stamp is
// a test source whose content hashes the sources a snippet can see; any edit
// changes it, and with `recompileAllFraction` at zero one changed test source
// recompiles them all. Found in scala-type-programming; see its build.sbt.
lazy val snippetSettings = Seq(
  Test / sourceGenerators += Def.task {
    val seen = (Compile / unmanagedSources).value ++
      (LocalProject("core") / Compile / unmanagedSources).value ++
      (LocalProject("core") / Test / unmanagedSources).value
    val hash = Hash.toHex(Hash(seen.sortBy(_.getPath).map(f => IO.read(f)).mkString("\u0000")))
    val stamp = (Test / sourceManaged).value / "SnippetScopeStamp.scala"
    val content = s"// Hash of the sources snippets type-check against: $hash\n"
    if (!stamp.exists || IO.read(stamp) != content) IO.write(stamp, content)
    Seq(stamp)
  }.taskValue,
  Test / incOptions := Def.uncached((Test / incOptions).value.withRecompileAllFraction(0.0))
)

// The course's own vocabulary: `unsolved`, the evidence helpers, and — in its
// *test* sources — `CourseSuite`, the base class every chapter's specs extend.
lazy val core = project
  .in(file("modules/core"))
  .settings(commonSettings, strictSettings, name := "advfp-core")

// `test->test` is what publishes CourseSuite to the chapters: sbt does not put
// a module's test classes on a dependent's test classpath otherwise.
lazy val chapterDeps = core % "compile->compile;test->test"

// The exercises. Every file compiles as it ships; an unsolved one throws
// `Unsolved` when called, so it shows up as a red test, never as a build that
// will not compile.
lazy val exercises = project
  .in(file("modules/exercises"))
  .dependsOn(chapterDeps)
  .settings(commonSettings, snippetSettings, name := "advfp-exercises")

// The answer key. It proves every spec can be satisfied, and the gate holds it
// green.
lazy val solutions = project
  .in(file("modules/solutions"))
  .dependsOn(chapterDeps)
  .settings(commonSettings, strictSettings, snippetSettings, name := "advfp-solutions")

// The typeclass hierarchy the milestones share, written from scratch. One
// module so that `laws` checks the same `Monad` the kernel implements; see
// docs/kernel/decisions/0001-a-typeclasses-module.md.
lazy val typeclasses = project
  .in(file("modules/typeclasses"))
  .settings(commonSettings, strictSettings, name := "advfp-typeclasses")

// Milestone 1, written from scratch: property-based testing and law suites.
// Nothing of the course's own depends on it — the chapters' specs must not
// lean on code the learner is still writing.
lazy val laws = project
  .in(file("modules/laws"))
  .dependsOn(typeclasses)
  .settings(commonSettings, strictSettings, name := "advfp-laws")

// Milestones 2 to 6, written from scratch: the pipeline kernel. Its tests use
// the law checker; its main sources depend on the typeclasses only.
lazy val kernel = project
  .in(file("modules/kernel"))
  .dependsOn(typeclasses, laws % "test->compile")
  .settings(commonSettings, strictSettings, name := "advfp-kernel")

// Scratch space. Nothing here is verified and nothing depends on it. The
// exercises are on its classpath, so a walkthrough can be poked at from the
// console.
lazy val playground = project
  .in(file("modules/playground"))
  .dependsOn(chapterDeps, exercises)
  .settings(commonSettings, name := "advfp-playground")

lazy val root = project
  .in(file("."))
  .aggregate(core, exercises, solutions, typeclasses, laws, kernel, playground)
  .settings(
    name := "scala-advanced-fp",
    publish / skip := true
  )

// The gate, and the only definition of "green" in this repository: everything
// formatted, the helpers in core passing their own tests, the answer key and
// the three milestone modules passing, and the exercises still compiling.
// `scripts/finish-chapter.sh` runs exactly this.
//
// `testFull`, not `test`: under sbt 2 the `test` task is incremental — it runs
// what failed last time, what never ran, and what a changed dependency touched,
// and nothing else. Excellent while working, useless as a gate, because the
// greenest possible run of it is the one that ran no tests at all.
addCommandAlias(
  "verify",
  "scalafmtCheckAll; scalafmtSbtCheck; core/testFull; solutions/testFull; " +
    "typeclasses/testFull; laws/testFull; kernel/testFull; exercises/Test/compile"
)

// The practice run. Red by design — every unsolved exercise is a failure here,
// and the failures are the to-do list.
addCommandAlias("practice", "exercises/testFull")
