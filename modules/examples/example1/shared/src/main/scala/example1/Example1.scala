package example1

import zio.*
import zio.config.yaml.YamlConfigProvider.fromYamlString
import zio.pgcopy.Copy
import zio.pgcopy.Copy.MakeError
import zio.stream.*

trait Example1:
  def run: ZIO[Scope, Any, Unit]

object Example1 extends ZIOAppDefault:

  lazy val layer = ZLayer.fromFunction(make)

  inline private given makeError: MakeError[String] = (a: Any) => a.toString

  private def make(copy: Copy) =
    new Example1:
      def run =
        import Fact.*
        val n = 100000
        val loop = for
          s <- Random.nextIntBetween(1, 500)
          _ <- ZIO.sleep(s.milliseconds)
          facts <- randomFacts(n)
          _ <- copy
            .in(s"fact (aggregateid,aggregatelatest,eventcategory,eventid,eventdatalength,eventdata,tags)", ZStream.fromChunk(facts))
            .measured(s"copyIn")
          _ <- ZIO.scoped(
            copy
              .out[String, Fact](
                s"select serialid,created,aggregateid,aggregatelatest,eventcategory,eventid,eventdatalength,eventdata,tags from fact",
                n
              )
              .flatMap(_.runCount)
              .measured(s"copyOut")
          )
        yield ()
        loop.repeatN(9)

  val program =
    ZIO
      .service[Example1]
      .provideSome[Scope](Example1.layer, Copy.layer)
      .flatMap(_.run)
      .catchAllCause(ZIO.logErrorCause(_))
      .forkDaemon
      .repeatN(7)
      *> ZIO.sleep(40.seconds)

  val run = program
    .withConfigProvider(ConfigProvider.defaultProvider.orElse(fromYamlString(readResourceFile("config.yml"))))
    .exitCode
