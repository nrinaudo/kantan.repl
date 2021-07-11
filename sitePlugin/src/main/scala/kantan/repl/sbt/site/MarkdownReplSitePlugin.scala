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

package kantan.repl.sbt.site

import com.typesafe.sbt.site.SitePlugin
import com.typesafe.sbt.site.SitePlugin.autoImport._
import kantan.repl.sbt.MarkdownReplPlugin
import kantan.repl.sbt.MarkdownReplPlugin.autoImport._
import sbt.Keys._
import sbt._

object MarkdownReplSiteSitePlugin extends AutoPlugin {
  override def trigger  = allRequirements
  override def requires = SitePlugin && MarkdownReplPlugin

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    MdRepl / siteSubdirName := ".",
    MdRepl / mappings       := mdRepl.value.pair(Path.relativeTo(mdReplTargetDirectory.value)),
    addMappingsToSiteDir(MdRepl / mappings, MdRepl / siteSubdirName)
  )
}
