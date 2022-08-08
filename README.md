# scala-sbt-deps-graph

It is a work-in-progress scala project to collect all the scala repositories from github and 
parse the project sbt files to help providing project information like dependencies, modules, etc.,


This project uses the following libraries
 - [Github4s](https://47degrees.github.io/github4s) a scala functional github client.
 - [Scalameta](https://scalameta.org/) a scala metaprogramming library for parsing the sbt files.


To run, since it is simple app.
```shell
sbt run
```