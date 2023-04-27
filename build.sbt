import CustomUtil._

ThisBuild / organization := "com.guidoschmidt17"
ThisBuild / organizationName := "Guido Schmidt"
ThisBuild / organizationHomepage := Some(url("https://guidoschmidt17.com"))
ThisBuild / licenses := Seq(License.Apache2)
ThisBuild / developers := List(Developer("guidoschmidt17", "Guido Schmidt", "guidoschmidt17@gmail.com", url("https://guidoschmidt17.com")))
ThisBuild / scmInfo := Some(
  ScmInfo(url("https://github.com/guidoschmidt17/zio-pgcopy.git"), "scm:git@github.com/guidoschmidt17/zio-pgcopy.git")
)
ThisBuild / description := "zio-pgcopy offers very fast bulk inserts and selects with a Postgresql database based on the binary wire protocol."

ThisBuild / publishTo := {
  val nexus = "https://s01.oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")
}
ThisBuild / pomIncludeRepository := { _ => false }
ThisBuild / publishMavenStyle := true

ThisBuild / version := "0.1.0-RC1"

ThisBuild / showSuccess := false
Global / excludeLintKeys ++= Set(showSuccess, publishMavenStyle, pomIncludeRepository)
Global / onChangedBuildSource := ReloadOnSourceChanges
logLevel := Level.Warn

ThisBuild / scalaVersion := "3.3.0-RC4"

val ZioVersion = "2.0.13"
val ZioConfigVersion = "4.0.0-RC14"
val NettyVersion = "4.1.92.Final"
val ScramVersion = "2.1"

name := "zio-pgcopy"

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

lazy val `simple` =
  crossProject(JVMPlatform)
    .crossType(CrossType.Full)
    .in(file("modules/examples/simple"))
    .dependsOn(`zio-pgcopy` % Cctt)
    .settings(commonSettings)
    .settings(
      libraryDependencies ++= Seq(
        "dev.zio" %%% "zio-config" % ZioConfigVersion,
        "dev.zio" %%% "zio-config-yaml" % ZioConfigVersion
      )
    )
    .settings(
      assembly / mainClass := Some("simple.Main")
    )
    .jvmSettings(
      assembly / assemblyMergeStrategy := {
        case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.first
        case x                                                    => (assembly / assemblyMergeStrategy).value(x)
      }
    )

lazy val commonSettings = Seq(Test / console / scalacOptions := (Compile / console / scalacOptions).value)
