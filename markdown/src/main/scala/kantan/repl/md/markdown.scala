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

package kantan.repl.md.markdown

import java.io.{File, FileOutputStream, OutputStreamWriter, StringWriter, Writer}
import scala.io.Source

/** One block in a markdown file.
  *
  * This is hugely simplified, because we really only care whether a block needs to go through the REPL or not. As a
  * result, we only have two kinds of blocks: `Repl` (must be processed) or `Other` (must not be processed).
  */
enum Block {
  case Repl(value: String, modifier: Modifier, startAt: Int)
  case Other(value: String)
}

enum Modifier {
  case Fail
  case Invisible
  case Print
  case Silent
}

private def isReplBlockStart(line: String) = line.startsWith("```scala repl")

private def isReplBlockEnd(line: String) = line.trim() == "```"

private def extractModifier(line: String, lineNumber: Int) = {
  val index = line.indexOf(':')

  // No modifier, simple print
  if index < 0 then Modifier.Print
  else
    line.splitAt(index + 1)(1) match {
      case "print" | "" => Modifier.Print
      case "invisible"  => Modifier.Invisible
      case "silent"     => Modifier.Silent
      case "fail"       => Modifier.Fail
      case modifier =>
        println(s"Unexpected modifier line $lineNumber: $modifier. Defaulting to normal printing.")
        Modifier.Print
    }
}

def load(source: Source): List[Block] =
  load(source.getLines)

/** Parses the specified markdown file as a list of blocks.
  *
  * Note that saying this supports markdown is a bit of a stretch. We're simply looking for triple-backtick enclosed
  * blocks with the expected label and ignoring everything else.
  *
  * This is not meant to be pretty or even very stable: the point is to have something that replaces tut and works with
  * Scala 3 up and running quickly. All of this code will be deprecated in a hurry.
  */
def load(lines: Iterator[String]): List[Block] = {

  // Current parser state: either in a REPL block, or not.
  enum State {
    case Repl(modifier: Modifier, line: Int)
    case Other
  }

  val blocks = List.newBuilder[Block]
  val block  = new StringBuilder()
  var state  = State.Other

  // Returns the content of the current block.
  // Note that we're trying to generate a canonical AST, so this removes all unnecessary whitespace.
  def currentBlock() = {
    val res = block.result()
    block.setLength(0)
    res.trim()
  }

  def appendRepl(content: String, modifier: Modifier, startAt: Int) =
    blocks += Block.Repl(content, modifier, startAt)

  def appendOther(content: String) =
    if content.nonEmpty then blocks += Block.Other(content)

  lines.zipWithIndex.foreach { case (line, number) =>
    state match {
      case State.Repl(modifier, startAt) if isReplBlockEnd(line) =>
        state = State.Other
        appendRepl(currentBlock(), modifier, startAt)

      case State.Other if isReplBlockStart(line) =>
        state = State.Repl(extractModifier(line, number), number)
        appendOther(currentBlock())

      case _ =>
        block ++= line
        block  += '\n'
    }
  }

  // Empties whatever might remain from the last block. Note that this is a bit lenient: if the last block is
  // a REPL block, and it's not properly closed, we'll ignore it silently.
  if block.nonEmpty then
    state match {
      case State.Repl(modifier, startAt) => appendRepl(currentBlock(), modifier, startAt)
      case State.Other                   => appendOther(currentBlock())
    }

  blocks.result()
}

def toString(blocks: List[Block]): String = {
  val out = new StringWriter

  print(blocks, out)

  out.toString
}

def print(blocks: List[Block], file: File): Unit = {
  val out = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")
  print(blocks, out)
  out.close()
}

def print(blocks: List[Block], out: Writer): Unit = {
  def nonEmpty(block: Block) = block match {
    case Block.Other(content) => content.trim.nonEmpty
    case _                    => true
  }

  def printBlock(block: Block) = block match {
    case Block.Other(value) =>
      out.write(value.trim())
      out.write("\n")

    case Block.Repl(value, modifier, _) =>
      out.write("```scala repl")
      modifier match {
        case Modifier.Silent    => out.write(":silent")
        case Modifier.Fail      => out.write(":fail")
        case Modifier.Invisible => out.write(":invisible")
        case Modifier.Print     =>
      }
      out.write('\n')
      out.write(value.trim())
      out.write("\n```\n")
  }

  var isFirst = true
  blocks.filter(nonEmpty).foreach { block =>
    if isFirst then isFirst = false
    else out.write("\n")
    printBlock(block)
  }

}
