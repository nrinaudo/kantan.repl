addSbtPlugin("com.github.sbt"    % "sbt-release"   % "1.4.0")
addSbtPlugin("com.github.sbt"    % "sbt-pgp"       % "2.3.0")
addSbtPlugin("org.scalameta"     % "sbt-scalafmt"  % "2.4.6")
addSbtPlugin("de.heikoseeberger" % "sbt-header"    % "5.6.0")
addSbtPlugin("org.xerial.sbt"    % "sbt-sonatype"  % "3.11.2")
addSbtPlugin("com.eed3si9n"      % "sbt-buildinfo" % "0.12.0")

libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value
