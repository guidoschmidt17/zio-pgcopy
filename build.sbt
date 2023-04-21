import CustomUtil._

ThisBuild / organization := "zio-pgcopy"
ThisBuild / licenses := Seq(License.Apache2)
ThisBuild / developers := List(Developer("guidoschmidt17", "Guido Schmidt", "", url("https://github.com/guidoschmidt17/zio-pgcopy")))
ThisBuild / scalaVersion := "3.2.2"

ThisBuild / version := "0.1.0"

ThisBuild / showSuccess := false

Global / excludeLintKeys += showSuccess
Global / onChangedBuildSource := ReloadOnSourceChanges

logLevel := Level.Warn

name := "zio-pgcopy"

val ZioVersion = "2.0.13"
val ZioConfigVersion = "4.0.0-RC14"
val NettyVersion = "4.1.91.Final"
val ScramVersion = "2.1"

lazy val `zio-pgcopy` =
  crossProject(JVMPlatform)
    .crossType(CrossType.Full)
    .in(file("modules/zio-pgcopy"))
    .settings(commonSettings)
    .settings(
      libraryDependencies ++= Seq(
        "dev.zio" %%% "zio" % ZioVersion,
        "dev.zio" %%% "zio-streams" % ZioVersion,
        "dev.zio" %%% "zio-test" % ZioVersion % Test,
        "dev.zio" %%% "zio-test-sbt" % ZioVersion % Test
      ),
      testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
    )
    .jvmSettings(
      libraryDependencies ++= Seq(
        "io.netty" % "netty-all" % NettyVersion,
        "com.ongres.scram" % "client" % ScramVersion
      )
    )

lazy val `facts` =
  crossProject(JVMPlatform)
    .crossType(CrossType.Full)
    .in(file("modules/examples/facts"))
    .dependsOn(`zio-pgcopy` % Cctt)
    .settings(commonSettings)
    .settings(
      libraryDependencies ++= Seq(
        "dev.zio" %%% "zio-config" % ZioConfigVersion,
        "dev.zio" %%% "zio-config-yaml" % ZioConfigVersion
      )
    )
    .settings(
      assembly / mainClass := Some("facts.Main")
    )
    .jvmSettings(
      assembly / assemblyMergeStrategy := {
        case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.first
        case x                                                    => (assembly / assemblyMergeStrategy).value(x)
      }
    )

lazy val commonSettings = Seq(Test / console / scalacOptions := (Compile / console / scalacOptions).value)
