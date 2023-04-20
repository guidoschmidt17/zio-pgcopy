import CustomUtil._
import org.scalajs.linker.interface.ModuleInitializer
import org.scalajs.linker.interface.ModuleSplitStyle
import org.scalajs.linker.interface.OutputPatterns

ThisBuild / organization := "zio-pgcopy"
ThisBuild / licenses := Seq(License.Apache2)
ThisBuild / developers := List(Developer("guidoschmidt17", "Guido Schmidt", "", url("https://github.com/guidoschmidt17/eventsourcing-3")))
ThisBuild / scalaVersion := "3.2.2"

ThisBuild / showSuccess := false

Global / excludeLintKeys += showSuccess
Global / onChangedBuildSource := ReloadOnSourceChanges

logLevel := Level.Warn

name := "zio-pgcopy"

lazy val `zio-pgcopy` =
  crossProject(JVMPlatform)
    .crossType(CrossType.Full)
    .in(file("modules/zio-pgcopy"))
    .settings(commonSettings)
    .settings(
      libraryDependencies ++= Seq(
        "dev.zio" %%% "zio" % "2.0.13",
        "dev.zio" %%% "zio-streams" % "2.0.13",
        "dev.zio" %%% "zio-test" % "2.0.13" % Test,
        "dev.zio" %%% "zio-test-sbt" % "2.0.13" % Test
      ),
      testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework")),
      Test / fork := true,
      run / fork := true
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

lazy val commonSettings = Seq(Test / console / scalacOptions := (Compile / console / scalacOptions).value)
