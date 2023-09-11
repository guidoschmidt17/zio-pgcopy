ThisBuild / scalaVersion := "2.12.17"
ThisBuild / useSuperShell := false
ThisBuild / autoStartServer := false
ThisBuild / showSuccess := false
Global / excludeLintKeys += showSuccess
ThisBuild / logLevel := Level.Warn

update / evictionWarningOptions := EvictionWarningOptions.empty

addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.11.1")
addSbtPlugin("com.timushev.sbt" % "sbt-rewarn" % "0.1.3")
addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.6.4")
addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "1.0.2")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.13.1")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.3.1")
addSbtPlugin("org.typelevel" % "sbt-typelevel" % "0.4.22")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.1.1")
addSbtPlugin("org.jmotor.sbt" % "sbt-dependency-updates" % "1.2.7")
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.2.1")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.9.21")
