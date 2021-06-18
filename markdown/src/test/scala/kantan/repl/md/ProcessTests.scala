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

package kantan.repl.md

import kantan.repl.md.scalatest.{failAtPosition, work}
import kantan.repl.Repl
import kantan.repl.md.cli
import kantan.repl.md.markdown.Block
import kantan.repl.md.markdown
import kantan.repl.md.process
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import scala.io.Source

class ProcessTests extends AnyFunSuite with Matchers {

  def evaluate(resource: String, opts: String*) =
    process.evaluate(
      new Repl(cli.rootOptions ++ opts),
      resource,
      markdown.load(Source.fromResource(s"examples/$resource"))
    )

  test("Expected compile failures are ignored") {
    evaluate("expected_compile_error.md") should work
  }

  test("Unexpected compile failures are caught") {
    evaluate("unexpected_compile_error.md") should failAtPosition(4, 10)
  }

  test("Expected runtime failures are ignored") {
    evaluate("expected_runtime_error.md") should work
  }

  test("Unexpected runtime failures are caught") {
    evaluate("unexpected_runtime_error.md") should failAtPosition(4, 9)
  }

  test("Expected successes are ignored") {
    evaluate("expected_success.md") should work
  }

  test("Unexpected successes are caught") {
    evaluate("unexpected_success.md") should failAtPosition(4, 0)
  }

  test("Non-fatal warnings are ignored") {
    evaluate("warning.md") should work
  }

  test("Fatal warnings are treated as errors") {
    evaluate("warning.md", "-Xfatal-warnings") should failAtPosition(4, 31)
  }

}
