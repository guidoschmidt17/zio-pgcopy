package facts

import zio.*
import zio.config.yaml.YamlConfigProvider.fromYamlString
import zio.pgcopy.*
import zio.stream.*

trait Main:
  def run: ZIO[Scope, Any, Unit]

object Main extends ZIOAppDefault:

  lazy val layer = ZLayer.fromFunction(make)

  given MakeError[String] = _.toString

  val sessions = 9
  val repeats = 29

  private def make(copy: Copy) = new Main:
    def run =
      import Fact.*
      val n = 100000
      val in = s"fact(aggregateid,aggregatelatest,eventcategory,eventid,eventdatalength,eventdata,tags)"
      val out = s"select aggregateid,aggregatelatest,eventcategory,eventid,eventdatalength,eventdata,tags from fact"
      val loop = for
        f <- randomFacts(n)
        _ <- copy.in(in, ZStream.fromChunk(f).rechunk(32 * 1024)).measured(s"copy.in")
        _ <- ZIO.scoped(copy.out[String, Fact](out, n).flatMap(_.runDrain).measured(s"copy.out"))
      yield ()
      loop.repeatN(repeats)

  val program = ZIO
    .service[Main]
    .provideSome[Scope](Main.layer, Copy.layer)
    .flatMap(_.run.forkDaemon.repeatN(sessions))
    .catchAllCause(ZIO.logErrorCause(_))
    *> ZIO.sleep(90.seconds)

  val run = program
    .withConfigProvider(ConfigProvider.defaultProvider.orElse(fromYamlString(readResourceFile("config.yml"))))
    .exitCode