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

package kantan.repl.md.scalacheck

import kantan.repl.md.markdown.{Block, Modifier}
import kantan.repl.md.process.ProcessedBlock
import org.scalacheck.{Arbitrary, Gen, Shrink}
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Shrink.shrink

given Arbitrary[String] = Arbitrary(Gen.identifier)

val lines: Gen[String] = arbitrary[List[String]].map(_.mkString("\n"))

val modifier: Gen[Modifier] = Gen.oneOf(Modifier.Fail, Modifier.Invisible, Modifier.Print, Modifier.Silent)

val repl: Gen[Block.Repl] = for {
  value    <- lines
  modifier <- modifier
  startAt  <- Gen.choose(0, 1000)
} yield Block.Repl(value, modifier, startAt)

val other: Gen[Block.Other] = lines.map(Block.Other.apply)

given Arbitrary[Block] = Arbitrary(Gen.oneOf(repl, other))

given Shrink[Block] = Shrink {
  case other: Block.Other =>
    shrink(other.value).map(Block.Other.apply)

  case code: Block.Repl =>
    shrink(code.value).map(value => code.copy(value = value)) #:::
      shrink(code.startAt).map(startAt => code.copy(startAt = startAt))
}

val processedRepl: Gen[ProcessedBlock.Repl] = for {
  input    <- lines
  modifier <- modifier
  output   <- lines
} yield ProcessedBlock.Repl(input, modifier, output)

given Arbitrary[ProcessedBlock.Repl] = Arbitrary(processedRepl)

given Shrink[ProcessedBlock.Repl] = Shrink { block =>

  val shrunkInput: Stream[ProcessedBlock.Repl]  = shrink(block.input).map(input => block.copy(input = input))
  val shrunkOutput: Stream[ProcessedBlock.Repl] = shrink(block.output).map(output => block.copy(output = output))

  shrunkInput #::: shrunkOutput
}
