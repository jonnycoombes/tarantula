import play.soap.sbtplugin.Imports.WsdlKeys

name := "jcs-facade"

version := "1.0.1"

lazy val `facade` = (project in file(".")).enablePlugins(PlayScala)

resolvers += "Akka Snapshot Repository" at "https://repo.akka.io/snapshots/"

scalaVersion := Versions.ScalaVersion
// Core play framework dependencies
libraryDependencies ++= Seq(jdbc, ehcache, ws, specs2 % Test, guice)
// JDBC related dependencies, including auth binaries for x64
libraryDependencies += "com.microsoft.sqlserver" % "mssql-jdbc" % Versions.SqlJdbcVersion
libraryDependencies += "com.microsoft.sqlserver" % "mssql-jdbc_auth" % Versions.SqlJdbcAuthVersion
libraryDependencies += "org.playframework.anorm" %% "anorm" % Versions.AnormVersion
libraryDependencies += "com.typesafe.play" %% "play-json-joda" % Versions.PlayJodaVersion

// Custom set of WsdlTasks in order to take into account inaccuracies in the Otcs Cws WSDL definitions
Compile / WsdlKeys.wsdlTasks := Seq(
  WsdlKeys.WsdlTask((baseDirectory.value / "wsdl" / "Authentication.wsdl").toURI.toURL,
    packageName = Some("com.opentext.cws.authentication")
  ),
  WsdlKeys.WsdlTask((baseDirectory.value / "wsdl" / "AdminService.wsdl").toURI.toURL,
    packageName = Some("com.opentext.cws.admin")
  ),
  WsdlKeys.WsdlTask((baseDirectory.value / "wsdl" / "ContentService.wsdl").toURI.toURL,
    packageName = Some("com.opentext.cws.content")
  ),
  WsdlKeys.WsdlTask((baseDirectory.value / "wsdl" / "DocumentManagement.wsdl").toURI.toURL,
    packageName = Some("com.opentext.cws.docman"),
    args = Seq("-autoNameResolution")
  )
)