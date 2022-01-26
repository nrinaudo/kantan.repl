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

package kantan.repl.md.process

import dotty.tools.dotc.reporting.{Diagnostic, Message}
import kantan.repl.{Repl, RuntimeError}
import kantan.repl.md.markdown.{Block, Modifier}
import java.io.File
import scala.util.{Failure, Success, Try}

// - Error handling ----------------------------------------------------------------------------------------------------
// ---------------------------------------------------------------------------------------------------------------------

/** Errors that can occur while processing some input markdown: unexpected errors or unexpected successes. */
abstract class UnexpectedException extends Exception {
  def source: String
  def line: Int
  def column: Int
  def error: String

  override def getMessage = s"""|$source:$line:$column
                                |$error""".stripMargin
}

object UnexpectedException {
  case class Error(source: String, line: Int, column: Int, error: String) extends UnexpectedException

  case class Success(source: String, line: Int) extends UnexpectedException {
    override val column = 0
    override val error  = "Excepted an error but encountered none."
  }
}

// - Block type --------------------------------------------------------------------------------------------------------
// ---------------------------------------------------------------------------------------------------------------------

/** Represents a markdown block after being processed through the repl. */
enum ProcessedBlock {
  case Repl(input: String, modifier: Modifier, output: String)
  case Other(content: String)
}

// - Logic -------------------------------------------------------------------------------------------------------------
// ---------------------------------------------------------------------------------------------------------------------

private def formatCompilerOutput(diag: Diagnostic, header: String): String = {
  val aligner = " " * header.length

  val report = diag.pos.lineContent + diag.pos.startColumnPadding + "^"

  s"$report\n$header" + diag.message.trim().replace("\n", s"\n$aligner")
}

private def formatDiagnostic(diag: Diagnostic): String =
  diag match {
    case _: RuntimeError       => diag.message.trim()
    case _: Diagnostic.Error   => formatCompilerOutput(diag, "⛔ ")
    case _: Diagnostic.Warning => formatCompilerOutput(diag, "⚠ ")
    case _                     => diag.message.trim()
  }

private def formatOutput(diags: List[Diagnostic]): String = {
  val raw = diags.map(formatDiagnostic).mkString("\n")

  if raw.nonEmpty then "// " + raw.replace("\n", "\n// ")
  else raw
}

private def firstError(diags: List[Diagnostic]): Option[Diagnostic] = diags.find {
  case _: Diagnostic.Error => true
  case _                   => false
}

def evaluate(repl: Repl, source: String, blocks: List[Block]): Try[List[ProcessedBlock]] = Try {

  blocks.map {
    case Block.Other(content) =>
      ProcessedBlock.Other(content)

    case Block.Repl(code, modifier, startAt) =>
      if(modifier == Modifier.Reset)
        repl.resetToInitial()

      val diags = repl.run(code + "\n")

      firstError(diags) match {
        // We need to add 2 to the error line because:
        // - lines are 0-indexed internally, but should be reported 1-indexed
        // - we need to take the block declaration (```scala repl) into account.
        case Some(error) if modifier != Modifier.Fail =>
          throw new UnexpectedException.Error(
            source = source,
            line = startAt + error.pos.line + 2,
            column = error.pos.column,
            error = s"${error.pos.lineContent}${error.pos.startColumnPadding}^\n${error.message.trim()}"
          )

        case None if modifier == Modifier.Fail =>
          throw new UnexpectedException.Success(
            source = source,
            line = startAt + 2
          )

        case _ =>
      }

      ProcessedBlock.Repl(code, modifier, formatOutput(diags))
  }
}
