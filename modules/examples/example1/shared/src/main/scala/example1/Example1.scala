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

  inline private given MakeError[String] = _.toString

  private def make(copy: Copy) =
    new Example1:
      def run =
        import Fact.*
        val n = 100000
        val loop =
          for
            s <- Random.nextIntBetween(1, 100)
            _ <- ZIO.sleep(s.milliseconds)
            facts <- randomFacts(n)
            _ <- copy
              .in(
                s"fact (aggregateid,aggregatelatest,eventcategory,eventid,eventdatalength,eventdata,tags,big)",
                ZStream.fromChunk(facts).rechunk(32 * 1024)
              )
              .measured(s"copy.in")
            _ <- ZIO.scoped(
              copy
                .out[String, Fact](
                  s"select aggregatelatest,eventcategory,eventid,eventdatalength,eventdata,tags,big from fact",
                  n
                )
                .flatMap(_.runDrain)
                .measured(s"copy.out")
            )
          yield ()
        loop.repeatN(19)

  val program =
    ZIO
      .service[Example1]
      .provideSome[Scope](Example1.layer, Copy.layer)
      .flatMap(_.run.forkDaemon.repeatN(7))
      .catchAllCause(ZIO.logErrorCause(_))
      *> ZIO.sleep(60.seconds)

  val run = program
    .withConfigProvider(ConfigProvider.defaultProvider.orElse(fromYamlString(readResourceFile("config.yml"))))
    .exitCode
