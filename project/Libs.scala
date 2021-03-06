import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import sbt.Def.{setting ⇒ dep}
import sbt._
import scalapb.compiler.Version.scalapbVersion

object Libs {
  val ScalaVersion   = "2.12.8"
  val AcyclicVersion = "0.1.8"

  val `scalatest` = dep("org.scalatest" %%% "scalatest" % "3.0.5") //Apache License 2.0

  val `scala-java8-compat` = "org.scala-lang.modules" %% "scala-java8-compat" % "0.9.0" //BSD 3-clause "New" or "Revised" License
  val `scala-async`        = dep("org.scala-lang.modules" %% "scala-async" % "0.9.7") //BSD 3-clause "New" or "Revised" License
  val `scopt`              = "com.github.scopt" %% "scopt" % "3.7.1" //MIT License
  val `acyclic`            = "com.lihaoyi" %% "acyclic" % AcyclicVersion % Provided //MIT License
  val `junit`              = "junit" % "junit" % "4.12" //Eclipse Public License 1.0
  val `junit-interface`    = "com.novocode" % "junit-interface" % "0.11" //BSD 2-clause "Simplified" License
  val `mockito-scala`      = "org.mockito" %% "mockito-scala" % "1.1.4"
  //MIT License
  val `embedded-keycloak`            = "com.github.tmtsoftware" % "embedded-keycloak"             % "v0.0.10"    //Apache 2.0
  val `logback-classic`              = "ch.qos.logback"         % "logback-classic"               % "1.2.3"      //Dual license: Either, Eclipse Public License v1.0 or GNU Lesser General Public License version 2.1
  val `akka-management-cluster-http` = "com.lightbend.akka"     %% "akka-management-cluster-http" % "0.6"        //N/A at the moment
  val `svnkit`                       = "org.tmatesoft.svnkit"   % "svnkit"                        % "1.9.3"      //TMate Open Source License
  val `commons-codec`                = "commons-codec"          % "commons-codec"                 % "1.10"       //Apache 2.0
  val `scala-reflect`                = "org.scala-lang"         % "scala-reflect"                 % ScalaVersion //BSD-3
  val `gson`                         = "com.google.code.gson"   % "gson"                          % "2.8.5"      //Apache 2.0

  val `play-json` = dep("com.typesafe.play" %%% "play-json" % "2.7.1") //Apache 2.0

  val `akka-http-play-json`      = "de.heikoseeberger"    %% "akka-http-play-json"      % "1.25.2" //Apache 2.0
  val `jwt-play-json`            = "com.pauldijou"        %% "jwt-play-json"            % "2.1.0"
  val `scalapb-runtime`          = "com.thesamet.scalapb" %% "scalapb-runtime"          % scalapbVersion % "protobuf"
  val `scalapb-json4s`           = "com.thesamet.scalapb" %% "scalapb-json4s"           % scalapbVersion
  val `lettuce`                  = "io.lettuce"           % "lettuce-core"              % "5.0.5.RELEASE"
  val `reactor-core`             = "io.projectreactor"    % "reactor-core"              % "3.2.6.RELEASE"
  val `reactive-streams`         = "org.reactivestreams"  % "reactive-streams"          % "1.0.2"
  val `akka-stream-kafka`        = "com.typesafe.akka"    %% "akka-stream-kafka"        % "0.22"
  val `scalatest-embedded-kafka` = "net.manub"            %% "scalatest-embedded-kafka" % "1.1.0"
  val `embedded-redis`           = "com.github.kstyrc"    % "embedded-redis"            % "0.6"
  val `scala-compiler`           = "org.scala-lang"       % "scala-compiler"            % ScalaVersion
  val `HdrHistogram`             = "org.hdrhistogram"     % "HdrHistogram"              % "2.1.11"
  val `testng`                   = "org.testng"           % "testng"                    % "6.14.3"

  val `scala-csv`                = "com.github.tototoshi" %% "scala-csv" % "1.3.5"
  val `scalajs-java-time`        = dep("org.scala-js" %%% "scalajs-java-time" % "0.2.5")
  val `json-schema-validator`    = "com.github.fge" % "json-schema-validator" % "2.2.8" //LGPL/ASL
  val `play-json-derived-codecs` = dep("org.julienrf" %%% "play-json-derived-codecs" % "5.0.0")

  val `jna`             = "net.java.dev.jna"              % "jna"             % "5.2.0"
  val `postgresql`      = "org.postgresql"                % "postgresql"      % "42.2.5"
  val `hikaricp`        = "com.zaxxer"                    % "HikariCP"        % "3.3.1" //Apache License 2.0
  val `otj-pg-embedded` = "com.opentable.components"      % "otj-pg-embedded" % "0.13.1"
  val httpclient        = "org.apache.httpcomponents"     % "httpclient"      % "4.5.7"
  val `jboss-logging`   = "org.jboss.logging"             % "jboss-logging"   % "3.3.2.Final"
  val `config`          = "com.typesafe"                  % "config"          % "1.3.3"
  val `os-lib`          = "com.lihaoyi"                   %% "os-lib"         % "0.2.7"
  val `caffeine`        = "com.github.ben-manes.caffeine" % "caffeine"        % "2.6.2"
}

object Typelevel {
  val Version     = "1.6.0"
  val `cats-core` = "org.typelevel" %% "cats-core" % Version
}

object Jackson {
  val Version                = "2.9.8"
  val `jackson-core`         = "com.fasterxml.jackson.core" % "jackson-core" % Version
  val `jackson-databind`     = "com.fasterxml.jackson.core" % "jackson-databind" % Version
  val `jackson-module-scala` = "com.fasterxml.jackson.module" %% "jackson-module-scala" % Version
}

object Enumeratum {
  val `enumeratum`           = dep("com.beachape" %%% "enumeratum"           % "1.5.13") //MIT License
  val `enumeratum-play-json` = dep("com.beachape" %%% "enumeratum-play-json" % "1.5.16") //MIT License
}

object Chill {
  val Version           = "0.9.3"
  val `chill-akka`      = "com.twitter" %% "chill-akka" % Version //Apache License 2.0
  val `chill-bijection` = "com.twitter" %% "chill-bijection" % Version //Apache License 2.0
}

object Akka {
  val Version                    = "2.5.21" //all akka is Apache License 2.0
  val `akka-stream`              = "com.typesafe.akka" %% "akka-stream" % Version
  val `akka-stream-typed`        = "com.typesafe.akka" %% "akka-stream-typed" % Version
  val `akka-remote`              = "com.typesafe.akka" %% "akka-remote" % Version
  val `akka-stream-testkit`      = "com.typesafe.akka" %% "akka-stream-testkit" % Version
  val `akka-actor`               = "com.typesafe.akka" %% "akka-actor" % Version
  val `akka-actor-typed`         = "com.typesafe.akka" %% "akka-actor-typed" % Version
  val `akka-actor-testkit-typed` = "com.typesafe.akka" %% "akka-actor-testkit-typed" % Version
  val `akka-distributed-data`    = "com.typesafe.akka" %% "akka-distributed-data" % Version
  val `akka-multi-node-testkit`  = "com.typesafe.akka" %% "akka-multi-node-testkit" % Version
  val `akka-cluster-tools`       = "com.typesafe.akka" %% "akka-cluster-tools" % Version
  val `akka-cluster`             = "com.typesafe.akka" %% "akka-cluster" % Version
  val `akka-cluster-typed`       = "com.typesafe.akka" %% "akka-cluster-typed" % Version
  val `akka-slf4j`               = "com.typesafe.akka" %% "akka-slf4j" % Version
}

object AkkaHttp {
  val Version             = "10.1.7"
  val `akka-http`         = "com.typesafe.akka" %% "akka-http" % Version //ApacheV2
  val `akka-http-testkit` = "com.typesafe.akka" %% "akka-http-testkit" % Version //ApacheV2
  val `akka-http-cors`    = "ch.megard" %% "akka-http-cors" % "0.3.4"
}

object Keycloak {
  val Version                 = "4.8.3.Final"
  val `keycloak-adapter-core` = "org.keycloak" % "keycloak-adapter-core" % Version
  val `keycloak-core`         = "org.keycloak" % "keycloak-core" % Version
  val `keycloak-installed`    = "org.keycloak" % "keycloak-installed-adapter" % Version
  val `keycloak-authz`        = "org.keycloak" % "keycloak-authz-client" % Version
  val `keycloak-test-helper`  = "org.keycloak" % "keycloak-test-helper" % Version
}

object Jooq {
  val Version        = "3.11.9"
  val `jooq`         = "org.jooq" % "jooq" % Version
  val `jooq-meta`    = "org.jooq" % "jooq-meta" % Version
  val `jooq-codegen` = "org.jooq" % "jooq-codegen" % Version
}
