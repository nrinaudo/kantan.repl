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

package kantan.repl.md.cache

import io.circe.{Decoder, Encoder}
import io.circe.parser.parse
import io.circe.syntax._
import java.io.{File, FileOutputStream, OutputStreamWriter}
import kantan.repl.md.process.ProcessedBlock
import kantan.repl.md.markdown.{Block, Modifier}
import scala.collection.mutable.Builder
import scala.io.Source
import scala.util.{Failure, Success, Try}

// - Type class instances ----------------------------------------------------------------------------------------------
// ---------------------------------------------------------------------------------------------------------------------
private given Encoder[Modifier] = Encoder[String].contramap {
  case Modifier.Reset     => "reset"
  case Modifier.Fail      => "fail"
  case Modifier.Invisible => "invisible"
  case Modifier.Print     => "print"
  case Modifier.Silent    => "silent"
}

private given Decoder[Modifier] = Decoder[String].emap {
  case "reset"     => Right(Modifier.Reset)
  case "fail"      => Right(Modifier.Fail)
  case "invisible" => Right(Modifier.Invisible)
  case "print"     => Right(Modifier.Print)
  case "silent"    => Right(Modifier.Silent)
  case other       => Left(s"Not a valid modifier: $other")
}

private given Decoder[ProcessedBlock.Repl] =
  Decoder.forProduct3[ProcessedBlock.Repl, String, Modifier, String]("input", "modifier", "output") {
    case (input, modifier, output) =>
      ProcessedBlock.Repl(input, modifier, output)
  }

private given Encoder[ProcessedBlock.Repl] = Encoder.forProduct3("input", "modifier", "output") { block =>
  (block.input, block.modifier, block.output)
}

// - Cache IO ----------------------------------------------------------------------------------------------------------
// ---------------------------------------------------------------------------------------------------------------------

/** Attempts to read the cache found in the specified file.
  *
  * Errors are treated as the absence of cache.
  */
def read(file: File): Option[List[ProcessedBlock.Repl]] = Try {

  if file.exists then {
    val json = Source.fromFile(file).mkString
    val cache = for {
      raw   <- parse(json)
      cache <- raw.as[List[ProcessedBlock.Repl]]
    } yield cache

    cache.toOption
  }
  else None
}.getOrElse {
  println(s"Failed to load cache from $file, assuming empty cache.")
  None
}

/** Attempts to write the specified cache to the specified file. */
def write(cache: List[ProcessedBlock.Repl], file: File): Try[Unit] = Try {
  val out = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")
  out.write(cache.asJson.toString)
  out.close()
}

// - Cache application -------------------------------------------------------------------------------------------------
// ---------------------------------------------------------------------------------------------------------------------

def extract(blocks: List[ProcessedBlock]): List[ProcessedBlock.Repl] = blocks.collect {
  case block: ProcessedBlock.Repl => block
}

/** Attempts to apply the specified cache to the specified input.
  *
  * If the cache doesn't match the input, we'll just return `None`.
  */
def use(input: List[Block], cache: List[ProcessedBlock.Repl]): Option[List[ProcessedBlock]] = {

  def matches(block: Block.Repl, cached: ProcessedBlock.Repl) =
    block.value == cached.input && block.modifier == cached.modifier

  def loop(
    acc: Builder[ProcessedBlock, List[ProcessedBlock]],
    blocks: List[Block],
    cache: List[ProcessedBlock.Repl]
  ): Option[List[ProcessedBlock]] = blocks match {
    case Block.Other(content) :: blocksTail =>
      loop(acc += ProcessedBlock.Other(content), blocksTail, cache)

    case (repl: Block.Repl) :: blocksTail =>
      cache match {
        case cached :: cacheTail if matches(repl, cached) => loop(acc += cached, blocksTail, cacheTail)
        case _                                            => None
      }

    case Nil =>
      Some(acc.result())
  }

  loop(List.newBuilder, input, cache)
}
