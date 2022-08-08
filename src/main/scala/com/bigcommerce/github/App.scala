package com.bigcommerce.github

import cats.data.NonEmptyList
import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits.{catsSyntaxSemigroup, toTraverseOps}
import github4s.domain.{Content, Pagination, Repository}
import github4s.{GHError, GHResponse, Github}
import org.http4s.client.{Client, JavaNetClientBuilder}

import scala.meta._
import java.util.Base64

object App extends IOApp {
  val accessToken: Option[String] = sys.env.get("GITHUB_TOKEN")
  val organization = "bigcommerce"
  val httpClient: Client[IO] = {
    JavaNetClientBuilder[IO].create // You can use any http4s backend
  }
  val ghc: Github[IO] = Github[IO](httpClient, accessToken)

  override def run(args: List[String]): IO[ExitCode] = {

    val result = for {
      repoResponse <- getOrgRepos(ghc, organization)
      contentPair <- foreachRepo(
        repoResponse,
        filterScalaReposAndGetSbtContentAsString(ghc),
        (error: GHError) => IO(List.empty[(Repository, Option[String])])
      )
     nonEmptySbtFiles <- IO(contentPair.filter(_._2.isDefined))
      _ <- nonEmptySbtFiles.map(data => {
        val tree: Option[Source] = parseSbtToSourceTree(data._2)
        IO.println(s"${data._1.name} ::: ${tree.map(_.stats)}")
      }).sequence
    } yield (ExitCode.Success)
    result
  }

  val filterScalaReposAndGetSbtContentAsString
      : Github[IO] => List[Repository] => IO[List[(Repository, Option[String])]] =
    ghc =>
      (repos) => {
        val scalaRepos = repos
          .filter(repo => repo.language.contains("Scala"))

        val data = scalaRepos
          .map(repo =>
            getSbtContents(
              ghc,
              repo,
            )).sequence
        data
      }

  val repoAsString: Repository => String = repo =>
    s" ${repo.name} :: ${repo.language.getOrElse("")} :: ${repo.urls.git_url}"

  val errorAsString: (String) => (GHError) => String = (key) =>
    (error) => s"$key. ${error.getMessage()}"
  val notFoundError: GHError => String = errorAsString("NOT FOUND")
  val readError: GHError => String = errorAsString("ERROR READ")

  def getOrgRepos(
      ghc: Github[IO],
      org: String
  ): IO[GHResponse[List[Repository]]] = ghc.repos.listOrgRepos(
    org = organization,
    pagination = Some(Pagination(0, 100))
  )

  def foreachRepo[A](
      response: GHResponse[List[Repository]],
      vfn: List[Repository] => A,
      efn: (GHError) => A
  ): A = {
    response.result match {
      case Left(error)  => efn(error)
      case Right(repos) => vfn(repos)
    }
  }

  val contentsAsString: NonEmptyList[Content] => Option[String] = contents => {
    contents
      .map(data => {
        data.encoding match {
          case Some("base64") => decodeContentToString(data)
          case None           => data.content
        }
      })
      .reduce((line1: Option[String], line2: Option[String]) => {
        line1 |+| line2
      })
  }

  def getSbtContents(ghc: Github[IO], repo: Repository): IO[(Repository, Option[String])] = {
    for {
      response <- ghc.repos.getContents(repo.owner.login, repo.name, "build.sbt", Some("master"))
    } yield ((repo, readContentsAs(response, contentsAsString, error => None)))
  }

  def readContentsAs[T](
      response: GHResponse[NonEmptyList[Content]],
      vfn: NonEmptyList[Content] => T,
      efn: (GHError) => T
  ): T = {
    response.result match {
      case Left(error) => {
        efn(error)
      }
      case Right(contents) => {
        vfn(contents)
      }
    }
  }

  def decodeContentToString(data: Content): Option[String] = data.content.map(encodedData =>
    new String(Base64.getMimeDecoder.decode(encodedData))
  )

  def parseSbtToSourceTree(data: Option[String]): Option[Source] = data.map(dialects.Sbt1(_).parse[Source].get)

}
