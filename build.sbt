ThisBuild / scalaVersion := "2.13.8"
ThisBuild / name := (root / name).value

lazy val commonSettings: Seq[Setting[_]] = Seq(
  version := sys.env.getOrElse("CI_COMMIT_TAG", "0.0.1-SNAPSHOT"),
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
  libraryDependencies ++= Seq(
    "org.scalameta" %% "munit" % "0.7.29" % Test,
    "de.lolhens" %% "munit-tagless-final" % "0.2.0" % Test,
    "ch.qos.logback" % "logback-classic" % "1.2.11" % Test
  ),
  testFrameworks += new TestFramework("munit.Framework"),
)

val V = new {
  val catsEffect = "3.3.11"
  val fs2 = "3.2.7"
  val http4s = "0.23.11"
  val nifi = "1.16.1"
}

lazy val root = project
  .in(file("."))
  .enablePlugins(NarPlugin)
  .settings(commonSettings)
  .settings(
    name := "nifi-scala",
    nifiVersion := "1.15.3",
    libraryDependencies ++= Seq(
      "org.apache.nifi" % "nifi-api" % V.nifi,
      "org.apache.nifi" % "nifi-utils" % V.nifi,
      "org.typelevel" %% "cats-effect" % V.catsEffect,
      "co.fs2" %% "fs2-io" % V.fs2,
    )
  )
