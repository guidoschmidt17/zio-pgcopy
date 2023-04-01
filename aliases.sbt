import CustomUtil._

addCommandAlias("l", "projects")
addCommandAlias("ll", "projects")
addCommandAlias("ls", "projects")
addCommandAlias("cd", "project")
addCommandAlias("root", "cd /")
addCommandAlias("c", "compile")
addCommandAlias("ca", "Test / compile")
addCommandAlias("t", "test")
addCommandAlias("r", "run")
addCommandAlias("rs", "reStart")
addCommandAlias("s", "reStop")
addCommandAlias("q", "exit")
addCommandAlias(
  "sc",
  "scalafmtSbtCheck; scalafmtCheckAll --exclude .metals/; Test / compile; scalafixAll --check"
)
addCommandAlias(
  "sf",
  "Test / compile; scalafixAll; scalafmtSbt; scalafmtAll --exclude .metals/"
)
addCommandAlias(
  "up2date",
  "dependencyUpdates"
)

onLoadMessage +=
  s"""|
      |╭─────────────────────────────────╮
      |│     List of defined ${styled("aliases")}     │
      |├─────────────┬───────────────────┤
      |│ ${styled("l")} | ${styled("ll")} | ${styled("ls")} │ projects          │sc
      |│ ${styled("cd")}          │ project           │
      |│ ${styled("root")}        │ cd root           │
      |│ ${styled("c")}           │ compile           │
      |│ ${styled("ca")}          │ compile all       │
      |│ ${styled("t")}           │ test              │
      |│ ${styled("r")}           │ run               │
      |│ ${styled("rs")}          │ reStart           │
      |│ ${styled("s")}           │ reStop            │
      |│ ${styled("q")}           │ exit              │
      |│ ${styled("sc")}          │ fmt & fix checks  │
      |│ ${styled("sf")}          │ fix then fmt      │
      |│ ${styled("up2date")}     │ dependencyUpdates │
      |╰─────────────┴───────────────────╯""".stripMargin
