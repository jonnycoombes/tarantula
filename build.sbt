name := "jcs-facade"
 
version := "1.0.1"
      
lazy val `facade` = (project in file(".")).enablePlugins(PlayScala)

      
resolvers += "Akka Snapshot Repository" at "https://repo.akka.io/snapshots/"
      
scalaVersion := Versions.ScalaVersion
// Core play framework dependencies
libraryDependencies ++= Seq( jdbc , ehcache , ws , specs2 % Test , guice )
// JDBC related dependencies, including auth binaries for x64
libraryDependencies += "com.microsoft.sqlserver" % "mssql-jdbc" % Versions.SqlJdbcVersion
libraryDependencies += "com.microsoft.sqlserver" % "mssql-jdbc_auth" % Versions.SqlJdbcAuthVersion
libraryDependencies +=  "org.playframework.anorm" %% "anorm" % Versions.AnormVersion

// WSDL related settings for SOAP bindings
WsdlKeys.packageName := Some("com.opentext")
WsdlKeys.wsdlToCodeArgs += "-autoNameResolution"