import CustomUtil._

ThisBuild / showSuccess := false
ThisBuild / logLevel := Level.Warn
Global / excludeLintKeys ++= Set(showSuccess, publishMavenStyle, pomIncludeRepository)
Global / onChangedBuildSource := ReloadOnSourceChanges

ThisBuild / scalaVersion := "3.3.1"

val ZioPgcopyVersion = "0.1.0-RC2"

val ZioVersion = "2.0.16"
val ZioConfigVersion = "4.0.0-RC16"
val NettyVersion = "4.1.97.Final"
val ScramVersion = "2.1"

ThisBuild / version := ZioPgcopyVersion

name := "zio-pgcopy"

lazy val `zio-pgcopy` =
  crossProject(JVMPlatform)
    .crossType(CrossType.Full)
    .in(file("modules/zio-pgcopy"))
    .settings(commonSettings)
    .settings(publishSettings)
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
    .dependsOn(`zio-pgcopy`)
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
    .dependsOn(`zio-pgcopy`)
    .settings(commonSettings)
    .settings(
      libraryDependencies ++= Seq(
        "dev.zio" %%% "zio-config" % ZioConfigVersion,
        "dev.zio" %%% "zio-config-yaml" % ZioConfigVersion
        // "com.guidoschmidt17" %%% "zio-pgcopy" % "0.1.0-RC1"
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

lazy val publishSettings = Seq(
  organization := "com.guidoschmidt17",
  organizationName := "Guido Schmidt",
  organizationHomepage := Some(url("https://guidoschmidt17.com")),
  licenses := Seq(License.Apache2),
  developers := List(Developer("guidoschmidt17", "Guido Schmidt", "", url("https://guidoschmidt17.com"))),
  scmInfo := Some(ScmInfo(url("https://github.com/guidoschmidt17/zio-pgcopy.git"), "scm:git@github.com/guidoschmidt17/zio-pgcopy.git")),
  description := "zio-pgcopy offers very fast bulk inserts and selects with a Postgresql database based on the binary wire protocol.",
  publishTo := sonatypePublishToBundle.value,
  sonatypeRepository := "https://s01.oss.sonatype.org/service/local",
  sonatypeCredentialHost := "s01.oss.sonatype.org",
  sonatypeProfileName := "com.guidoschmidt17",
  sonatypeProjectHosting := Some(Sonatype.GitHubHosting("guidoschmidt17", "zio-pgcopy", "", ""))
)
