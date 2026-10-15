import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

import scala.jdk.CollectionConverters.*
import scala.util.Using

import sbt.*
import sbt.Keys.*

/** Bridges sbt/sbt#9877 until the sbt pinned in `build.properties` carries its fix (the 2.0.x
  * backport, sbt/sbt#9879, merged after 2.0.10). Delete this file with that upgrade.
  *
  * sbt 2.0.10 restores cached SemanticDB files as symlinks into its CAS, which it has made
  * read-only. The next compile that misses the cache writes the `.semanticdb` files in place,
  * through the link, and fails with `java.nio.file.AccessDeniedException`; under Metals every
  * compile and test run then fails until the links are gone. This replaces them with writable
  * copies before Zinc runs, which is what the upstream fix does.
  */
object SemanticdbLinks extends AutoPlugin:
  override def trigger = allRequirements
  override def requires = plugins.JvmPlugin

  override def projectSettings: Seq[Setting[?]] =
    Seq(Compile, Test).map: config =>
      // `compileInputs` is uncached and an input of the cached compile, so this runs before Zinc
      // could, on every compile; a `dependsOn` on `compile` would race the compile instead.
      config / compile / compileInputs := Def.uncached {
        val inputs = (config / compile / compileInputs).value
        replaceLinks((config / semanticdbTargetRoot).value.toPath)
        inputs
      }

  private def replaceLinks(root: Path): Unit =
    if Files.isDirectory(root) then
      val links = Using.resource(Files.walk(root)): paths =>
        paths.iterator.asScala
          .filter(p => Files.isSymbolicLink(p) && p.getFileName.toString.endsWith(".semanticdb"))
          .toList
      links.foreach: link =>
        val copy = link.resolveSibling(s"${link.getFileName}.copy")
        Files.copy(link, copy, StandardCopyOption.REPLACE_EXISTING)
        copy.toFile.setWritable(true)
        Files.move(copy, link, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
end SemanticdbLinks
