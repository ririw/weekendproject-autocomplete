organization  := "cua.li"

version       := "0.2"

scalaVersion  := "2.10.3"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

resolvers ++= Seq(
  "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
  "Spray repository" at "http://repo.spray.io/"
)

libraryDependencies ++= {
  val akkaV = "2.2.3"
  val sprayV = "1.2.0"
  Seq(
    "io.spray"                        %% "spray-json"              % "1.2.5",
    "io.spray"                        % "spray-can"                % sprayV,
    "io.spray"                        % "spray-routing"            % sprayV,
    "com.typesafe.akka"               %% "akka-actor"              % akkaV,
    "com.typesafe.akka"               %% "akka-testkit"            % akkaV     % "test",
    "io.spray"                        % "spray-testkit"            % sprayV    % "test",
    "org.scalatest"                   %% "scalatest"               % "2.2.0"   % "test",
    "junit"                           % "junit"                    % "4.11"    % "test",
    "org.specs2"                      %% "specs2"                  % "2.2.3"   % "test",
    "org.apache.directory.studio"     % "org.apache.commons.io"    % "2.4",
    "com.codahale.metrics"            % "metrics-core"             % "3.0.2",
    "com.wandoulabs.akka"             %% "spray-websocket"         % "0.1.1-RC1",
    "org.scalacheck"                  %% "scalacheck"              % "1.11.4"  % "test"
  )
}


seq(Revolver.settings: _*)
