ThisBuild / scalacOptions ++= Seq(
  "-indent",
  "-language:implicitConversions",
  "-new-syntax",
  "-pagewidth:140",
  "-source:future",
  "-Xcheck-macros",
  "-Xfatal-warnings",
  "-Ysafe-init",
  "-Yexplicit-nulls",
  "-Ylightweight-lazy-vals"
)
