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

import kantan.repl.md.scalatest.matchProcessed
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Arbitrary.arbitrary
import io.circe.syntax._
import kantan.repl.md.markdown.{Block, Modifier}
import kantan.repl.md.scalacheck.{lines, given}
import kantan.repl.md.process.ProcessedBlock
import kantan.repl.md.cache.given
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class CacheTests extends AnyFunSuite with Matchers with ScalaCheckDrivenPropertyChecks {

  test("Cache should succesfully roundtrip") {
    forAll { (content: List[ProcessedBlock.Repl]) =>
      content.asJson.as[List[ProcessedBlock.Repl]] should be(Right(content))

    }
  }

  test("Up-to-date cache should be used") {
    forAll { (cached: Cached) =>

      val result = use(cached.input, cached.cache)
        .getOrElse(fail("Cache was up-to-date but considered stale"))

      result.length should be(cached.input.length)
      cached.input.zip(result).foreach { case (input, output) => input should matchProcessed(output) }
    }
  }

  test("Stale cache should be not used") {
    forAll(Cached.stale) { cached =>
      use(cached.input, cached.cache) should be(None)
    }
  }
}

case class Cached(input: List[Block], cache: List[ProcessedBlock.Repl])

object Cached {
  def apply(input: List[Block]): Cached = {
    val cache: List[ProcessedBlock.Repl] = input.collect { case Block.Repl(content, modifier, _) =>
      ProcessedBlock.Repl(content, modifier, content)
    }

    Cached(input, cache)
  }

  given Arbitrary[Cached] = Arbitrary(arbitrary[List[Block]].map(Cached.apply))

  val stale: Gen[Cached] = {
    // Modifiers a block in a way that should cause a cache invalidation:
    // - change its content
    // - change its modifier
    def perturb(block: ProcessedBlock.Repl): Gen[ProcessedBlock.Repl] = {

      def changeModifier: Gen[ProcessedBlock.Repl] = {
        val modifiers = Set(Modifier.Fail, Modifier.Invisible, Modifier.Print, Modifier.Silent) - block.modifier

        Gen.oneOf(modifiers).map(m => block.copy(modifier = m))
      }

      def changeContent: Gen[ProcessedBlock.Repl] =
        lines.filter(_ != block.input).map(content => block.copy(input = content))

      Gen.oneOf(changeModifier, changeContent)
    }

    // Modifies the specified cache in a way that should cause it to be invalidated.
    def invalidate(cache: List[ProcessedBlock.Repl]): Gen[List[ProcessedBlock.Repl]] =
      for {
        index   <- Gen.choose(0, cache.length - 1)
        updated <- perturb(cache(index))
      } yield cache.updated(index, updated)

    for {
      cached      <- arbitrary[Cached] if cached.cache.nonEmpty
      invalidated <- invalidate(cached.cache)
    } yield cached.copy(cache = invalidated)
  }
}
