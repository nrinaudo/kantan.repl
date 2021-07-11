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

import kantan.repl.md.markdown
import kantan.repl.md.markdown.Block
import kantan.repl.md.scalacheck.given
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import scala.io.Source

class MarkdownTests extends AnyFunSuite with Matchers with ScalaCheckDrivenPropertyChecks {

  // This test is slightly weaker than it could be, because our AST is a little bit awkward: it's perfectly possible
  // to generate multiple ASTs that serialise to the same output.
  // I would ideally write a property like decode(encode(input)) == input, but this is not guaranteed to hold.
  // What *should* hold is that the markdown serialisation of two equivalent ASTs is the same.
  // Unfortunately, that property holds for the no-op serialiser, even though it's clearly not what we want.
  test("Printing, parsing and printing should yield the same result") {
    forAll { (content: List[Block]) =>
      val trip1 = markdown.toString(content)
      val trip2 = markdown.toString(markdown.load(Source.fromString(trip1)))

      trip1 should be(trip2)
    }
  }
}
