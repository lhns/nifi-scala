lazy val scalaVersions = Seq("3.2.0", "2.13.8")

ThisBuild / scalaVersion := scalaVersions.head
ThisBuild / versionScheme := Some("early-semver")

lazy val commonSettings: Seq[Setting[_]] = Seq(
  organization := "de.lolhens",
  version := {
    val Tag = "refs/tags/(.*)".r
    sys.env.get("CI_VERSION").collect { case Tag(tag) => tag }
      .getOrElse("0.0.1-SNAPSHOT")
  },

  licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0")),

  homepage := scmInfo.value.map(_.browseUrl),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/lhns/nifi-scala"),
      "scm:git@github.com:lhns/nifi-scala.git"
    )
  ),
  developers := List(
    Developer(id = "lhns", name = "Pierre Kisters", email = "pierrekisters@gmail.com", url = url("https://github.com/lhns/"))
  ),

  libraryDependencies ++= Seq(
    "ch.qos.logback" % "logback-classic" % "1.4.1" % Test,
    "de.lolhens" %% "munit-tagless-final" % "0.2.0" % Test,
    "org.scalameta" %% "munit" % "0.7.29" % Test,
  ),

  testFrameworks += new TestFramework("munit.Framework"),

  libraryDependencies ++= virtualAxes.?.value.getOrElse(Seq.empty).collectFirst {
    case VirtualAxis.ScalaVersionAxis(version, _) if version.startsWith("2.") =>
      compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
  },

  Compile / doc / sources := Seq.empty,

  publishMavenStyle := true,

  publishTo := sonatypePublishToBundle.value,

  credentials ++= (for {
    username <- sys.env.get("SONATYPE_USERNAME")
    password <- sys.env.get("SONATYPE_PASSWORD")
  } yield Credentials(
    "Sonatype Nexus Repository Manager",
    "oss.sonatype.org",
    username,
    password
  )).toList
)

name := (core.projectRefs.head / name).value

val V = new {
  val catsEffect = "3.3.14"
  val fs2 = "3.2.14"
  val http4s = "0.23.11"
  val nifi = "1.17.0"
}

lazy val root: Project =
  project
    .in(file("."))
    .settings(commonSettings)
    .settings(
      publishArtifact := false,
      publish / skip := true
    )
    .aggregate(core.projectRefs: _*)

lazy val core = projectMatrix.in(file("core"))
  .settings(commonSettings)
  .settings(
    name := "nifi-scala",

    libraryDependencies ++= Seq(
      "org.apache.nifi" % "nifi-api" % V.nifi,
      "org.apache.nifi" % "nifi-utils" % V.nifi,
      "org.typelevel" %% "cats-effect" % V.catsEffect,
      "co.fs2" %% "fs2-io" % V.fs2,
    ),
  )
  .jvmPlatform(scalaVersions)
