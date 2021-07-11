import de.heikoseeberger.sbtheader.AutomateHeaderPlugin
import sbt._, Keys._
import sbt.plugins.{JvmPlugin, SbtPlugin}
import sbt.ScriptedPlugin.autoImport._
import sbtrelease.ReleasePlugin, ReleasePlugin.autoImport._, ReleaseTransformations._, ReleaseKeys._

object SbtBuildPlugin extends AutoPlugin {
  override def trigger = allRequirements

  override def requires = SbtPlugin

  override lazy val projectSettings = List(
    sbtPlugin           := true,
    scriptedLaunchOpts ++= Seq("-Xmx1024M", "-Dplugin.version=" + version.value),
    scriptedBufferLog   := false
  )
}

object BuildPlugin extends AutoPlugin {
  override def trigger = allRequirements

  override def requires = JvmPlugin && ReleasePlugin

  override lazy val projectSettings = baseSettings ++ releaseSettings

  override def globalSettings: Seq[Setting[_]] =
    addCommandAlias(
      "validate",
      ";clean;scalafmtCheck;Test /scalafmtCheck;scalafmtSbtCheck;compile;test;scripted"
    )

  lazy val runScripted: ReleaseStep = {
    val scriptedStep = releaseStepInputTask(scripted)
    ReleaseStep(
      action = { st: State =>
        if(!st.get(skipTests).getOrElse(false)) {
          scriptedStep(st)
        }
        else st
      }
    )
  }

  def releaseSettings: Seq[Setting[_]] =
    Seq(
      releaseProcess := Seq[ReleaseStep](
        checkSnapshotDependencies,
        inquireVersions,
        runClean,
        releaseStepCommand("scalafmtCheck"),
        releaseStepCommand("scalafmtSbtCheck"),
        runScripted,
        setReleaseVersion,
        commitReleaseVersion,
        tagRelease,
        releaseStepCommand("publishSigned"),
        releaseStepCommand("sonatypeReleaseAll"),
        setNextVersion,
        commitNextVersion,
        pushChanges
      )
    )

  def baseSettings: Seq[sbt.Def.Setting[_]] =
    Seq(
      organization         := "com.nrinaudo",
      organizationHomepage := Some(url("https://nrinaudo.github.io")),
      organizationName     := "Nicolas Rinaudo",
      startYear            := Some(2021),
      run / fork           := true,
      licenses             := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.html")),
      homepage             := Some(url(s"https://nrinaudo.github.io/kantan.dot")),
      publishTo := Some(
        if(isSnapshot.value)
          Opts.resolver.sonatypeSnapshots
        else
          Opts.resolver.sonatypeStaging
      ),
      developers := List(
        Developer("nrinaudo", "Nicolas Rinaudo", "nicolas@nrinaudo.com", url("https://twitter.com/nicolasrinaudo"))
      ),
      scmInfo := Some(
        ScmInfo(
          url(s"https://github.com/nrinaudo/kantan.repl"),
          s"scm:git:git@github.com:nrinaudo/kantan.repl.git"
        )
      ),
      scalacOptions ++= Seq(
        "-deprecation",
        "-encoding",
        "utf-8",
        "-feature",
        "-language:existentials",
        "-language:experimental.macros",
        "-language:higherKinds",
        "-language:implicitConversions",
        "-unchecked"
      )
    )
}
