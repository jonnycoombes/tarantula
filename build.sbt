name := "jcs-facade"
 
version := "1.0.1"
      
lazy val `facade` = (project in file(".")).enablePlugins(PlayScala)

      
resolvers += "Akka Snapshot Repository" at "https://repo.akka.io/snapshots/"
      
scalaVersion := Versions.ScalaVersion

libraryDependencies ++= Seq( jdbc , ehcache , ws , specs2 % Test , guice )

// WSDL related settings for SOAP bindings
WsdlKeys.packageName := Some("com.opentext")
WsdlKeys.wsdlToCodeArgs += "-autoNameResolution"