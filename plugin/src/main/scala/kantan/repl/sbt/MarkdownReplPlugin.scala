/*
 * Copyright 2021 Nicolas Rinaudo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kantan.repl.sbt

import java.io.ByteArrayInputStream
import sbt.Keys._
import sbt.Attributed.data
import sbt._
import sys.process._
import sbt.io.Path.FileMap

object MarkdownReplPlugin extends AutoPlugin {
  override def trigger = allRequirements

  object autoImport {
    lazy val MdRepl                = (config("MdRepl") extend Compile).hide
    lazy val mdReplSourceDirectory = settingKey[File]("where to look for md.repl sources")
    lazy val mdReplTargetDirectory = settingKey[File]("where md.repl output goes")
    lazy val mdReplNameFilter      = settingKey[FileFilter]("md.repl skips files whose names don't match")
    lazy val mdRepl                = taskKey[Seq[File]]("runs all md files through the Scala REPL")
  }

  import autoImport._

  override lazy val projectSettings: Seq[Setting[_]] = inConfig(MdRepl)(Defaults.configSettings) ++ Seq(
    libraryDependencies   += "com.nrinaudo" %% "kantan.repl-markdown" % BuildInfo.version % MdRepl,
    ivyConfigurations     += MdRepl,
    fork                  := true,
    mdReplNameFilter      := "*.markdown" || "*.md" || "*.htm" || "*.html",
    mdReplSourceDirectory := (Compile / sourceDirectory).value / "mdrepl",
    mdReplTargetDirectory := crossTarget.value / "mdrepl",
    mdRepl := processDirectory(
      runner.value,
      (MdRepl / fullClasspath).value,
      (MdRepl / scalacOptions).value,
      streams.value,
      mdReplSourceDirectory.value,
      Path.rebase(mdReplSourceDirectory.value, mdReplTargetDirectory.value),
      Path.rebase(mdReplSourceDirectory.value, streams.value.cacheDirectory),
      mdReplNameFilter.value
    )
  )

  /** Checks whether the input file is newer than the output one. */
  def mustReprocess(in: File, out: File): Boolean =
    in.lastModified >= out.lastModified

  /** Processes the content of the specified source directory, writing the result to the specified target directory. */
  def processDirectory(
    run: ScalaRun,
    cp: Classpath,
    scalacOptions: Seq[String],
    streams: TaskStreams,
    source: File,
    targetMapping: FileMap,
    cacheMapping: FileMap,
    nameFilter: FileFilter
  ): Seq[File] = {
    streams.log.info("running md.repl")

    val mapping = (file: File) =>
      for {
        target <- targetMapping(file)
        cache  <- cacheMapping(file)
      } yield (target, cache)

    (source ** nameFilter)
      .pair(mapping)
      .map { case (in, (out, cache)) =>
        if(mustReprocess(in, out))
          processFile(run, cp, scalacOptions, streams, in, out, cache)
        out
      }
  }

  /** Processes a single file. */
  def processFile(
    run: ScalaRun,
    cp: Classpath,
    scalacOptions: Seq[String],
    streams: TaskStreams,
    input: File,
    output: File,
    cache: File
  ): File = {
    streams.log.info(s"Compiling $input")

    output.getParentFile().mkdirs()
    cache.getParentFile().mkdirs()

    val out = run.run(
      "kantan.repl.md.cli.main",
      data(cp),
      Seq(input.getAbsolutePath, output.getAbsolutePath, cache.getAbsolutePath) ++ scalacOptions,
      streams.log
    )

    output
  }
}
