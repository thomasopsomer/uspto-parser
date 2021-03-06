// common settings
name := "uspto-parser"
version := "0.0.1"
scalaVersion := "2.10.5"
organization := "asgard"

enablePlugins(DockerPlugin)

// sbt spark plugin practice:
// the name of your Spark Package
spName := "asgard/uspto-parser"
// the Spark Version your package depends on.
sparkVersion := "1.6.3"
// sparkComponents += "mllib" // creates a dependency on spark-mllib.
sparkComponents += "sql"


libraryDependencies ++= Seq(
  "org.slf4j" % "slf4j-log4j12" % "1.7.21",
  "com.google.guava" % "guava" % "20.0",
  "org.jsoup" % "jsoup" % "1.10.2",
  "dom4j" % "dom4j" % "1.6.1",
  "xml-apis" % "xml-apis" % "1.4.01",
  "jaxen" % "jaxen" % "1.1.6",
  "com.github.scopt" %% "scopt" % "3.4.0",
  "com.amazonaws" % "aws-java-sdk-s3" % "1.9.0",
  "org.apache.commons" % "commons-compress" % "1.11",
  "net.sf.jopt-simple" % "jopt-simple" % "5.0.2"
)

// local dependencies (not on maven)
unmanagedBase := baseDirectory.value / "lib"


assemblyMergeStrategy in assembly := {
  case PathList("org", "w3c", xs @ _*)         => MergeStrategy.first
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}


dockerfile in docker := {
  // The assembly task generates a fat JAR file
  val artifact: File = assembly.value
  val artifactTargetPath = s"/home/uspto-parser/${artifact.name}"
  val log4jConfig: File = new File("log4j.properties")
  new Dockerfile {
    from("asgard/spark")
    add(artifact, artifactTargetPath)
    env("USPTO_PARSER_JAR_PATH", artifactTargetPath)
    add(log4jConfig, "/usr/spark/conf/log4j.properties")
    cmd("/sbin/my_init")
  }
}

imageNames in docker := Seq(
  // Sets the latest tag
  ImageName(s"${organization.value}/${name.value}:latest"),

  // Sets a name with a tag that contains the project version
  ImageName(
    namespace = Some(organization.value),
    repository = name.value,
    tag = Some("v" + version.value)
  )
)