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

package kantan.repl.md.scalatest

import kantan.repl.md.markdown.Block
import kantan.repl.md.process.{ProcessedBlock, UnexpectedException}
import org.scalactic.source
import org.scalatest.exceptions.StackDepthException
import org.scalatest.exceptions.TestFailedException
import scala.util.{Failure, Success, Try}
import org.scalatest.matchers.Matcher
import org.scalatest.matchers.should.Matchers._
import org.scalatest.matchers.MatchResult

def beLine(line: Int): Matcher[UnexpectedException] = Matcher(e =>
  MatchResult(
    e.line == line,
    s"Expected error to be line $line but found it line ${e.line}",
    s"Found error at expected line $line"
  )
)

def beColumn(column: Int): Matcher[UnexpectedException] = Matcher(e =>
  MatchResult(
    e.column == column,
    s"Expected error to be column $column but found it column ${e.column}",
    s"Found error at expected column $column"
  )
)

def failAtPosition(line: Int, column: Int)(using pos: source.Position): Matcher[Try[List[ProcessedBlock]]] =
  (beLine(line) and beColumn(column)).compose {
    case Failure(e: UnexpectedException) => e
    case _ => throw new TestFailedException(s"Expected a REPL error but didn't get one", pos.lineNumber)
  }

// I wish I could call this succeed, but that's apparently reserved by scalatest already and causes truly obscure
// compilation errors.
def work: Matcher[Try[List[ProcessedBlock]]] = Matcher(t =>
  MatchResult(
    t.isSuccess == true,
    "Excepted a success but got an error",
    "Got a success"
  )
)

def matchProcessed(expected: ProcessedBlock)(using pos: source.Position): Matcher[Block] = {
  def matchRepl(repl: ProcessedBlock.Repl): Matcher[Block.Repl] = be(repl.input).compose[Block.Repl](_.value) and
    be(repl.modifier).compose[Block.Repl](_.modifier)

  def matchOther(other: ProcessedBlock.Other): Matcher[Block.Other] = be(other.content).compose(_.value)

  def extractOther(block: Block) = block match {
    case _: Block.Repl =>
      throw new TestFailedException(s"Expected a non-REPL block but got a REPL block", pos.lineNumber)
    case other: Block.Other => other
  }

  def extractRepl(block: Block) = block match {
    case repl: Block.Repl => repl
    case _: Block.Other =>
      throw new TestFailedException(s"Expected a REPL block but got a non-REPL block", pos.lineNumber)
  }

  expected match {
    case other: ProcessedBlock.Other => matchOther(other).compose(extractOther)
    case repl: ProcessedBlock.Repl   => matchRepl(repl).compose(extractRepl)
  }
}
