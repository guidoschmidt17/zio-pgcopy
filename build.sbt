import CustomUtil._
import org.scalajs.linker.interface.ModuleInitializer
import org.scalajs.linker.interface.ModuleSplitStyle
import org.scalajs.linker.interface.OutputPatterns

ThisBuild / organization := "zio-pgcopy"
ThisBuild / scalaVersion := "3.2.2"

ThisBuild / showSuccess := false

Global / excludeLintKeys += showSuccess
Global / onChangedBuildSource := ReloadOnSourceChanges

logLevel := Level.Warn

lazy val `zio-pgcopy` =
  crossProject(JVMPlatform)
    .crossType(CrossType.Full)
    .in(file("modules/zio-pgcopy"))
    .settings(commonSettings)
    .settings(commonDependencies)
    .settings(
      libraryDependencies ++= Seq(
        "dev.zio" %%% "zio" % "2.0.10",
        "dev.zio" %%% "zio-streams" % "2.0.10"
      )
    )
    .jvmSettings(
      libraryDependencies ++= Seq(
        "io.netty" % "netty-all" % "4.1.91.Final",
        "com.ongres.scram" % "client" % "2.1"
      )
    )

lazy val `example-1` =
  crossProject(JVMPlatform)
    .crossType(CrossType.Full)
    .in(file("modules/examples/example1"))
    .dependsOn(`zio-pgcopy` % Cctt)
    .settings(commonSettings)
    .settings(commonDependencies)
    .settings(
      libraryDependencies ++= Seq(
        "dev.zio" %%% "zio-config" % "4.0.0-RC14",
        "dev.zio" %%% "zio-config-yaml" % "4.0.0-RC14"
      )
    )
    .settings(
      assembly / mainClass := Some("example1.Example1")
    )
    .jvmSettings(
      assembly / assemblyMergeStrategy := {
        case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.first
        case x                                                    => (assembly / assemblyMergeStrategy).value(x)
      }
    )

lazy val commonSettings = Seq(
  Test / console / scalacOptions :=
    (Compile / console / scalacOptions).value
)

lazy val commonDependencies = Seq(
  libraryDependencies ++= Seq(
    "org.scalacheck" %% "scalacheck" % "1.17.0",
    "org.scalatest" %% "scalatest" % "3.2.15",
    "org.scalatestplus" %% "scalacheck-1-15" % "3.2.11.0",
    "org.typelevel" %% "discipline-scalatest" % "2.2.0"
  ).map(_ % Test)
)
