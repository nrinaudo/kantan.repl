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

package kantan.repl

import dotty.tools.dotc.reporting.{Diagnostic, Message}
import dotty.tools.dotc.util.{NoSourcePosition, SourceFile, SourcePosition}
import java.io.{OutputStream, PrintStream}

/** Output stream that turns everything that's written to it into a `Diagnostic`. */
class DiagnosticOutputStream extends OutputStream {
  val builder = new StringBuilder

  override def write(c: Int) =
    builder.append(c.toChar)

  def content(): Option[Diagnostic] = {
    val out = builder.result()
    builder.clear()
    if out.nonEmpty then Some(new Diagnostic.Info(out, NoSourcePosition))
    else None
  }
}

object DiagnosticOutputStream {
  private val out = new DiagnosticOutputStream

  /** Captures the output of the specified `op` and appends it at the end of the corresponding list of diagnostics. */
  def wrap(op: => List[Diagnostic]): List[Diagnostic] = {
    val savedOut = System.out
    val savedErr = System.err

    val print = new PrintStream(out, true, "UTF-8")

    try {
      System.setOut(print)
      System.setErr(print)

      val diagnostics = op

      out.content() match {
        case Some(diag) => diagnostics :+ diag
        case None       => diagnostics
      }
    }
    finally {
      System.setOut(savedOut)
      System.setErr(savedErr)
    }
  }
}
