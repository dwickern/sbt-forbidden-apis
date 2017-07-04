name := "sbt-forbidden-apis"
organization := "com.github.dwickern"

sbtPlugin := true

libraryDependencies ++= Seq(
  "de.thetaphi" % "forbiddenapis" % "2.3" jar()
)

// set up 'scripted; sbt plugin for testing sbt plugins
ScriptedPlugin.scriptedSettings
scriptedLaunchOpts ++= Seq("-Xmx1024M", "-XX:MaxPermSize=256M", "-Dplugin.version=" + version.value)

releaseProcess := {
  import ReleaseTransformations._
  Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runTest,
    releaseStepInputTask(scripted), // added
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    publishArtifacts,
    setNextVersion,
    commitNextVersion,
    pushChanges
  )
}

pomExtra in Global := {
  <url>https://github.com/dwickern/sbt-forbidden-apis</url>
    <licenses>
      <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
      </license>
    </licenses>
    <scm>
      <connection>scm:git:github.com/dwickern/sbt-forbidden-apis.git</connection>
      <developerConnection>scm:git:git@github.com:dwickern/sbt-forbidden-apis.git</developerConnection>
      <url>github.com/dwickern/sbt-forbidden-apis.git</url>
    </scm>
    <developers>
      <developer>
        <id>dwickern</id>
        <name>Derek Wickern</name>
        <url>https://github.com/dwickern</url>
      </developer>
    </developers>
}