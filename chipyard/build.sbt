import Tests._

// This gives us a nicer handle  to the root project instead of using the
// implicit one
lazy val chipyardRoot = Project("chipyardRoot", file("."))

lazy val commonSettings = Seq(
  organization := "edu.berkeley.cs",
  version := "1.0",
  scalaVersion := "2.12.10",
  traceLevel := 15,
  test in assembly := {},
  assemblyMergeStrategy in assembly := { _ match {
    case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
    case _ => MergeStrategy.first}},
  scalacOptions ++= Seq("-deprecation","-unchecked","-Xsource:2.11"),
  libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.8" % "test",
  libraryDependencies += "org.json4s" %% "json4s-jackson" % "3.6.1",
  libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  libraryDependencies += "com.github.scopt" %% "scopt" % "3.7.0",
  libraryDependencies += "org.scala-lang.modules" % "scala-jline" % "2.12.1",
  libraryDependencies += "com.typesafe.play" %% "play-json" % "2.6.10",
  libraryDependencies += "org.typelevel" %% "spire" % "0.16.2",
  libraryDependencies += "org.scalanlp" %% "breeze" % "1.0",
  addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
  unmanagedBase := (chipyardRoot / unmanagedBase).value,
  allDependencies := allDependencies.value.filterNot(_.organization == "edu.berkeley.cs"),
  exportJars := true,
  resolvers ++= Seq(
    Resolver.sonatypeRepo("snapshots"),
    Resolver.sonatypeRepo("releases"),
    Resolver.mavenLocal))

val rocketChipDir = file("generators/rocket-chip")

lazy val firesimAsLibrary = sys.env.get("FIRESIM_STANDALONE") == None
lazy val firesimDir = if (firesimAsLibrary) {
  file("sims/firesim/sim/")
} else {
  file("../../sim")
}

// Checks for -DROCKET_USE_MAVEN.
// If it's there, use a maven dependency.
// Else, depend on subprojects in git submodules.
def conditionalDependsOn(prj: Project): Project = {
  if (sys.props.contains("ROCKET_USE_MAVEN")) {
    prj.settings(Seq(
      libraryDependencies += "edu.berkeley.cs" %% "testchipip" % "1.0-020719-SNAPSHOT",
    ))
  } else {
    prj.dependsOn(testchipip)
  }
}

/**
  * It has been a struggle for us to override settings in subprojects.
  * An example would be adding a dependency to rocketchip on midas's targetutils library,
  * or replacing dsptools's maven dependency on chisel with the local chisel project.
  *
  * This function works around this by specifying the project's root at src/ and overriding
  * scalaSource and resourceDirectory.
  */
def freshProject(name: String, dir: File): Project = {
  Project(id = name, base = dir / "src")
    .settings(
      scalaSource in Compile := baseDirectory.value / "main" / "scala",
      resourceDirectory in Compile := baseDirectory.value / "main" / "resources"
    )
}

// Fork each scala test for now, to work around persistent mutable state
// in Rocket-Chip based generators
def isolateAllTests(tests: Seq[TestDefinition]) = tests map { test =>
      val options = ForkOptions()
      new Group(test.name, Seq(test), SubProcess(options))
  } toSeq

// Subproject definitions begin
//
// FIRRTL is handled as an unmanaged dependency. Make will build the firrtl jar
// before launching sbt if any of the firrtl source files has been updated
// The jar is dropped in chipyard's lib/ directory, which is used as the unmanagedBase
// for all subprojects
lazy val chisel  = (project in file("tools/chisel3"))

lazy val firrtl_interpreter = (project in file("tools/firrtl-interpreter"))
  .settings(commonSettings)

lazy val treadle = (project in file("tools/treadle"))
  .settings(commonSettings)

lazy val chisel_testers = (project in file("tools/chisel-testers"))
  .dependsOn(chisel, firrtl_interpreter, treadle)
  .settings(
      commonSettings,
      libraryDependencies ++= Seq(
        "junit" % "junit" % "4.12",
        "org.scalatest" %% "scalatest" % "3.0.5",
        "org.scalacheck" %% "scalacheck" % "1.14.0",
        "com.github.scopt" %% "scopt" % "3.7.0"
      )
    )

// Contains annotations & firrtl passes you may wish to use in rocket-chip without
// introducing a circular dependency between RC and MIDAS
lazy val midasTargetUtils = ProjectRef(firesimDir, "targetutils")

 // Rocket-chip dependencies (subsumes making RC a RootProject)
lazy val hardfloat  = (project in rocketChipDir / "hardfloat")
  .settings(commonSettings).dependsOn(midasTargetUtils)

lazy val rocketMacros  = (project in rocketChipDir / "macros")
  .settings(commonSettings)

lazy val rocketConfig = (project in rocketChipDir / "api-config-chipsalliance/build-rules/sbt")
  .settings(commonSettings)

lazy val rocketchip = freshProject("rocketchip", rocketChipDir)
  .settings(commonSettings)
  .dependsOn(chisel, hardfloat, rocketMacros, rocketConfig)

lazy val testchipip = (project in file("generators/testchipip"))
  .dependsOn(rocketchip, sifive_blocks)
  .settings(commonSettings)

lazy val iocell = (project in file("./tools/barstools/iocell/"))
  .dependsOn(chisel)
  .settings(commonSettings)

lazy val chipyard = conditionalDependsOn(project in file("generators/chipyard"))
  .dependsOn(boom, hwacha, sifive_blocks, sifive_cache, utilities, iocell,
    sha3, // On separate line to allow for cleaner tutorial-setup patches
    dsptools, `rocket-dsptools`,
    gemmini, icenet, tracegen, ariane, nvdla,
    difuzzrtl)
  .settings(commonSettings)

lazy val tracegen = conditionalDependsOn(project in file("generators/tracegen"))
  .dependsOn(rocketchip, sifive_cache, boom, utilities)
  .settings(commonSettings)

lazy val utilities = conditionalDependsOn(project in file("generators/utilities"))
  .settings(commonSettings)

lazy val icenet = (project in file("generators/icenet"))
  .dependsOn(rocketchip, testchipip)
  .settings(commonSettings)

lazy val hwacha = (project in file("generators/hwacha"))
  .dependsOn(rocketchip)
  .settings(commonSettings)

lazy val boom = conditionalDependsOn(project in file("generators/boom"))
  .dependsOn(rocketchip)
  .settings(commonSettings)

lazy val ariane = (project in file("generators/ariane"))
  .dependsOn(rocketchip)
  .settings(commonSettings)

lazy val sha3 = (project in file("generators/sha3"))
  .dependsOn(rocketchip, chisel_testers, midasTargetUtils)
  .settings(commonSettings)

lazy val gemmini = (project in file("generators/gemmini"))
  .dependsOn(rocketchip, chisel_testers, testchipip)
  .settings(commonSettings)

lazy val nvdla = (project in file("generators/nvdla"))
  .dependsOn(rocketchip)
  .settings(commonSettings)

lazy val tapeout = conditionalDependsOn(project in file("./tools/barstools/tapeout/"))
  .dependsOn(chisel_testers, chipyard)
  .settings(commonSettings)
  .settings(libraryDependencies ++= Seq("io.github.daviddenton" %% "handlebars-scala-fork" % "2.3.0"))

lazy val mdf = (project in file("./tools/barstools/mdf/scalalib/"))
  .settings(commonSettings)

lazy val barstoolsMacros = (project in file("./tools/barstools/macros/"))
  .dependsOn(firrtl_interpreter, mdf, rocketchip)
  .enablePlugins(sbtassembly.AssemblyPlugin)
  .settings(commonSettings)

lazy val dsptools = freshProject("dsptools", file("./tools/dsptools"))
  .dependsOn(chisel, chisel_testers)
  .settings(
      commonSettings,
      libraryDependencies ++= Seq(
        "junit" % "junit" % "4.13" % "test",
        "org.scalatest" %% "scalatest" % "3.0.8",
        "org.scalacheck" %% "scalacheck" % "1.14.3" % "test"
  ))

lazy val `rocket-dsptools` = freshProject("rocket-dsptools", file("./tools/dsptools/rocket"))
  .dependsOn(rocketchip, dsptools)
  .settings(commonSettings)

lazy val sifive_blocks = (project in file("generators/sifive-blocks"))
  .dependsOn(rocketchip)
  .settings(commonSettings)

lazy val sifive_cache = (project in file("generators/sifive-cache")).settings(
    commonSettings,
    scalaSource in Compile := baseDirectory.value / "design/craft"
  ).dependsOn(rocketchip)

// Library components of FireSim
lazy val midas      = ProjectRef(firesimDir, "midas")
lazy val firesimLib = ProjectRef(firesimDir, "firesimLib")

lazy val firechip = conditionalDependsOn(project in file("generators/firechip"))
  .dependsOn(chipyard, midasTargetUtils, midas, firesimLib % "test->test;compile->compile")
  .settings(
    commonSettings,
    testGrouping in Test := isolateAllTests( (definedTests in Test).value ),
    testOptions in Test += Tests.Argument("-oF")
  )

lazy val difuzzrtl = (project in file("generators/difuzzrtl"))
  .dependsOn(rocketchip, boom)
  .settings(commonSettings)
