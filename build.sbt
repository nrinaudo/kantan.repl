lazy val root = Project(id = "kantan-repl", base = file("."))
  .settings(moduleName := "root")
  .aggregate(core, markdown, plugin, sitePlugin)

// Core classes, mostly Repl itself.
lazy val core = project
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    moduleName   := "kantan.repl",
    name         := "core",
    scalaVersion := Versions.scala3,
    libraryDependencies ++= Seq(
      "org.scala-lang" %% "scala3-compiler" % Versions.scala3
    )
  )

// Simple markdown integration.
lazy val markdown = project
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    moduleName   := "kantan.repl-markdown",
    name         := "markdown",
    scalaVersion := Versions.scala3,
    Test / fork  := true,
    libraryDependencies ++= Seq(
      "io.circe"          %% "circe-parser"    % Versions.circe,
      "org.scalacheck"    %% "scalacheck"      % Versions.scalacheck              % "test",
      "org.scalatest"     %% "scalatest"       % Versions.scalatest               % "test",
      "org.scalatestplus" %% "scalacheck-1-15" % Versions.scalatestPlusScalacheck % "test"
    )
  )
  .dependsOn(core)

// SBT plugin for the markdown integration.
lazy val plugin = project
  .enablePlugins(AutomateHeaderPlugin, SbtPlugin, BuildInfoPlugin)
  .settings(
    moduleName       := "kantan.repl-sbt",
    name             := "plugin",
    scalaVersion     := Versions.scala2,
    buildInfoKeys    := Seq[BuildInfoKey](version),
    buildInfoPackage := "kantan.repl.sbt"
  )

lazy val sitePlugin = project
  .enablePlugins(AutomateHeaderPlugin, SbtPlugin)
  .settings(
    moduleName   := "kantan.repl-sbt-site",
    name         := "sitePlugin",
    scalaVersion := Versions.scala2,
    addSbtPlugin("com.typesafe.sbt" % "sbt-site" % Versions.sbtSite)
  )
  .dependsOn(plugin)
