import sbt._

object Dependencies {
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.11"
  lazy val githubClient = "com.47deg" %% "github4s" % "0.31.0"
  lazy val catsEffect = "org.typelevel" %% "cats-effect" % "3.3.12"
  lazy val scalaMeta = "org.scalameta" %% "scalameta" % "4.5.11"

}
