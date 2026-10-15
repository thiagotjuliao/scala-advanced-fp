// A starting point, not a contract: unlike the other files in this template,
// build.sbt is expected to grow into something project-specific. What is worth
// keeping is the shape — the pinned Scala version, the compiler flags from
// project/CompilerFlags.scala, the test framework, `exportJars := false`, and
// the `verify` alias CI runs.

ThisBuild / scalaVersion := "3.9.0"
ThisBuild / organization := "dev.thiagojuliao"
ThisBuild / version := "0.1.0"

lazy val root = project
  .in(file("."))
  .settings(
    name := "scala-advanced-fp",
    // Tests run from the class directories, not from jars. With sbt 2's
    // default `exportJars := true` the test classpath is the packaged jar, and
    // when compile is served from the disk cache (sbt 2.0.9) the jar is not
    // repackaged: undoing an edit ran the tests against the last real compile,
    // and `clean testFull` ran no tests and reported success. Measured
    // 2026-10-02; keep it in every module of a multi-module build.
    exportJars := false,
    // project/CompilerFlags.scala names every flag; add groups or single
    // flags from there, e.g. `++ CompilerFlags.lint`.
    scalacOptions ++= CompilerFlags.base,
    // munit by default. For scalatest instead:
    //   libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.19" % Test
    // and drop the testFrameworks line — sbt finds scalatest on its own.
    libraryDependencies += "org.scalameta" %% "munit" % "1.3.6" % Test,
    testFrameworks += new TestFramework("munit.Framework")
  )

// The one definition of "green", run by CI and worth running before a push:
// every source formatted, and every test run.
//
// `testFull`, not `test`: under sbt 2 `test` is incremental — it runs what
// failed last time, what never ran, and what a changed dependency touched, and
// nothing else. Its greenest possible run is the one that ran no tests at all.
addCommandAlias("verify", "scalafmtCheckAll; scalafmtSbtCheck; testFull")
