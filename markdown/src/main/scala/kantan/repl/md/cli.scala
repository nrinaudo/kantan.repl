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

package kantan.repl.md.cli

import kantan.repl.md.{cache, markdown, process}
import kantan.repl.md.markdown.{Block, Modifier}
import kantan.repl.md.process.ProcessedBlock
import kantan.repl.Repl
import java.io.File
import scala.io.Source
import scala.util.CommandLineParser as CLP
import scala.util.{Failure, Success, Try}

private def toMarkdown(blocks: List[ProcessedBlock]): List[Block] = {

  def visible(block: ProcessedBlock) = block match
    case ProcessedBlock.Repl(_, Modifier.Invisible | Modifier.Reset, _) => false
    case _                                                              => true

  def scala(content: String) = s"```scala\n$content\n```"

  blocks.filter(visible).map {
    case ProcessedBlock.Other(content)                  => Block.Other(content)
    case ProcessedBlock.Repl(input, Modifier.Silent, _) => Block.Other(scala(input))
    case ProcessedBlock.Repl(input, modifier, output)   => Block.Other(scala(input ++ "\n" ++ output))
  }
}

given CLP.FromString[File] with
  def fromString(s: String) = new File(s)

val rootOptions = List(
  "-classpath",
  "", // Avoid the default "."
  "-usejavacp",
  "-color:never"
)

@main def main(source: File, destination: File, cacheFile: File, scalacOptions: String*) = {

  def readSource =
    Try(markdown.load(Source.fromFile(source)))

  def applyCache(markdown: List[Block]) = for
    cached <- cache.read(cacheFile)
    result <- cache.use(markdown, cached)
  yield Success(result)

  def runRepl(markdown: List[Block]) = {
    val repl = new Repl(rootOptions ++ scalacOptions)
    process.evaluate(repl, source.toString, markdown).map { processed =>
      val cached = cache.extract(processed)

      // We're ignoring errors here because the cache is just an optimisation, not a critical part of what we're
      // doing.
      cache.write(cached, cacheFile).getOrElse(println("Failed to write cache."))
      processed
    }
  }

  def printToDestination(result: List[ProcessedBlock]) =
    Try(markdown.print(toMarkdown(result), destination))

  val outcome = for
    markdown <- readSource
    cached = applyCache(markdown)
    result <- cached.getOrElse(runRepl(markdown))
    _      <- printToDestination(result)
  yield ()

  // Basic error handling.
  outcome match {
    case Failure(error) =>
      System.err.println(error.getMessage)
      System.exit(1)

    case _ =>
  }
}
