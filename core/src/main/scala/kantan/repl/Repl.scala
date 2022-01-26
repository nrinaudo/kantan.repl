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

import dotty.tools.repl._
import java.io.{File => JFile, PrintStream, OutputStream}
import java.nio.charset.StandardCharsets
import dotty.tools.dotc.ast.Trees._
import dotty.tools.dotc.ast.{tpd, untpd}
import dotty.tools.dotc.core.Contexts._
import dotty.tools.dotc.core.Phases.{typerPhase, unfusedPhases}
import dotty.tools.dotc.core.Denotations.Denotation
import dotty.tools.dotc.core.Flags._
import dotty.tools.dotc.core.Mode
import dotty.tools.dotc.core.NameKinds.SimpleNameKind
import dotty.tools.dotc.core.NameOps._
import dotty.tools.dotc.core.Names.Name
import dotty.tools.dotc.core.StdNames._
import dotty.tools.dotc.core.Symbols.{defn, Symbol}
import dotty.tools.dotc.interactive.Completion
import dotty.tools.dotc.printing.SyntaxHighlighting
import dotty.tools.dotc.reporting.MessageRendering
import dotty.tools.dotc.reporting.{Diagnostic, Message}
import dotty.tools.dotc.util.Spans.Span
import dotty.tools.dotc.util.{NoSourcePosition, SourceFile, SourcePosition}
import dotty.tools.dotc.{CompilationUnit, Driver}
import dotty.tools.dotc.config.CompilerCommand
import dotty.tools.io._
import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.util.Using
import dotty.tools.dotc.interfaces.Diagnostic.INFO

/** An error diagnostic that denotes errors that occurred at runtime. */
class RuntimeError(
  msg: Message,
  pos: SourcePosition
) extends Diagnostic.Error(msg, pos)

/** A Scala REPL, as a stateful function from `String` to `List[Diagnostic]`.
  *
  * This is basically a copy / paste of dotty's internal REPL, simplified for our far less complicated use case. I don't
  * pretend to understand most of this code, but it seems to work and that's good enough (for the moment).
  */
class Repl(settings: List[String]) extends Driver {

  /** Overridden to `false` in order to not have to give sources on the commandline
    */
  override def sourcesRequired: Boolean = false

  /** Create a fresh and initialized context with IDE mode enabled */
  private def initialCtx = {
    val rootCtx = initCtx.fresh.addMode(Mode.ReadPositions | Mode.Interactive)
    rootCtx.setSetting(rootCtx.settings.YcookComments, true)
    rootCtx.setSetting(rootCtx.settings.YreadComments, true)
    setup(settings.toArray, rootCtx) match {
      case Some((files, ictx)) =>
        ictx.base.initialize()(using ictx)
        ictx
      case None =>
        rootCtx
    }
  }

  /** the initial, empty state of the REPL session */
  final def initialState: State = State(0, 0, Map.empty, rootCtx)

  /** Reset state of repl to the initial state
    *
    * This method is responsible for performing an all encompassing reset. As such, when the user enters `:reset` this
    * method should be called to reset everything properly
    */
  def resetToInitial(): Unit = {
    rootCtx = initialCtx
    if rootCtx.settings.outputDir.isDefault(using rootCtx) then
      rootCtx = rootCtx.fresh
        .setSetting(rootCtx.settings.outputDir, new VirtualDirectory("<REPL compilation output>"))
    compiler = new ReplCompiler
    rendering = new Rendering(None)
    state = initialState
  }

  private var rootCtx: Context       = _
  private var compiler: ReplCompiler = _
  private var rendering: Rendering   = _
  private var state: State           = _

  // initialize the REPL session as part of the constructor so that once `run`
  // is called, we're in business
  resetToInitial()

  override protected def command: CompilerCommand = ReplCommand

  final def run(input: String): List[Diagnostic] = DiagnosticOutputStream.wrap {
    val parsed = ParseResult(input)(state)
    interpret(parsed)
  }

  private def newRun() = {
    val run = compiler.newRun(rootCtx.fresh.setReporter(newStoreReporter), state)
    state = state.copy(context = run.runContext)
  }

  private def interpret(res: ParseResult): List[Diagnostic] = {
    val output = res match {
      case parsed: Parsed if parsed.trees.nonEmpty =>
        compile(parsed)

      case SyntaxErrors(_, errs, _) =>
        errs

      case cmd: Command =>
        List.empty

      case SigKill =>
        List.empty

      case _ => // new line, empty tree
        List.empty
    }
    inContext(state.context) {
      output
    }
  }

  /** Compile `parsed` trees and evolve `state` in accordance */
  private def compile(parsed: Parsed): List[Diagnostic] = {
    def extractNewestWrapper(tree: untpd.Tree): Name = tree match {
      case PackageDef(_, (obj: untpd.ModuleDef) :: Nil) => obj.name.moduleClassName
      case _                                            => nme.NO_NAME
    }

    def extractTopLevelImports(ctx: Context): List[tpd.Import] =
      unfusedPhases(using ctx).collectFirst { case phase: CollectTopLevelImports => phase.imports }.get

    newRun()
    state = state.copy(context = state.context.withSource(parsed.source))

    compiler
      .compile(parsed)(state)
      .fold(
        errors => errors,
        { case (unit: CompilationUnit, newState: State) =>
          val newestWrapper = extractNewestWrapper(unit.untpdTree)
          val newImports    = extractTopLevelImports(newState.context)
          var allImports    = newState.imports
          if newImports.nonEmpty then allImports += (newState.objectIndex -> newImports)
          state = newState.copy(imports = allImports)

          val warnings = state.context.reporter
            .removeBufferedMessages(using state.context)

          inContext(newState.context) {
            val (updatedState, definitions) =
              renderDefinitions(unit.tpdTree, newestWrapper)

            // output is printed in the order it was put in. warnings should be
            // shown before infos (eg. typedefs) for the same line. column
            // ordering is mostly to make tests deterministic
            implicit val diagnosticOrdering: Ordering[Diagnostic] =
              Ordering[(Int, Int, Int)].on(d => (d.pos.line, -d.level, d.pos.column))

            (definitions ++ warnings).sorted.toList
          }
        }
      )
  }

  private def renderDefinitions(tree: tpd.Tree, newestWrapper: Name): (State, List[Diagnostic]) = {
    given Context = state.context

    def isRes(sym: Symbol) = {
      import scala.util.{Success, Try}
      val name = sym.name.show
      val hasValidNumber = Try(name.drop(3).toInt) match {
        case Success(num) => num < state.valIndex
        case _            => false
      }
      name.startsWith(str.REPL_RES_PREFIX) && hasValidNumber
    }

    def resAndUnit(denot: Denotation) = {
      val sym = denot.symbol
      isRes(sym) && sym.info == defn.UnitType
    }

    def extractAndFormatMembers(symbol: Symbol): (State, List[Diagnostic]) = if tree.symbol.info.exists then {
      val info = symbol.info

      val vals =
        info.fields
          .filterNot(_.symbol.isOneOf(ParamAccessor | Private | Synthetic | Artifact | Module))
          .filter(_.symbol.name.is(SimpleNameKind))
          .filter(field => isRes(field.symbol))

      val formattedMembers =
        vals.flatMap(rendering.renderVal)

      val diagnostics = if formattedMembers.isEmpty then rendering.forceModule(symbol) else formattedMembers

      (state.copy(valIndex = state.valIndex - vals.count(resAndUnit)), diagnostics.toList)
    }
    else (state, List.empty)

    atPhase(typerPhase.next) {
      // Display members of wrapped module:
      tree.symbol.info.memberClasses
        .find(_.symbol.name == newestWrapper.moduleClassName)
        .map { wrapperModule =>
          extractAndFormatMembers(wrapperModule.symbol)
        }
        .getOrElse {
          // user defined a trait/class/object, so no module needed
          (state, List.empty)
        }
    }
  }
}
