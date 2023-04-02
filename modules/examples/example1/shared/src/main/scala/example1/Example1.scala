package example1

import zio.*
import zio.config.yaml.YamlConfigProvider.fromYamlString
import zio.pgcopy.Field.fieldsExpression
import zio.pgcopy.PostgresCopy
import zio.pgcopy.PostgresCopy.MakeError
import zio.stream.*

trait Example1:
  def run: ZIO[Scope, Any, Unit]

object Example1 extends ZIOAppDefault:

  lazy val layer = ZLayer.fromFunction(make)

  inline private given makeError: MakeError[String] = (a: Any) => a.toString

  private def make(copy: PostgresCopy) =
    new Example1:
      def run =
        import Fact.*
        val n = 100000
        val loop = for
          s <- Random.nextIntBetween(50, 500)
          _ <- ZIO.sleep(s.milliseconds)
          facts <- randomFacts(n)
          instream = ZStream.fromChunk(facts).rechunk(32 * 1024)
          _ <- copy
            .copyIn(
              s"fact (aggregateid,aggregatelatest,eventcategory,eventid,eventdatalength,eventdata,tags)",
              instream,
              200
            )
            .measured(s"copyIn ${facts.size}")
          outstream <- copy.copyOut[String, Fact](
            s"select aggregatelatest,eventcategory,eventid,eventdatalength,eventdata,tags from fact",
            n
          )
          c <- outstream.runCount.measured(s"copyOut")
        yield ()
        loop.repeatN(9)

  val program =
    ZIO
      .service[Example1]
      .provideSome[Scope](Example1.layer, PostgresCopy.layer)
      .flatMap(_.run)
      .catchAllCause(ZIO.logErrorCause(_))
      .forkDaemon
      .repeatN(7)
      *> ZIO.sleep(2.minutes)

  val run = program
    .withRuntimeFlags(RuntimeFlags.disable(RuntimeFlag.FiberRoots))
    .withConfigProvider(ConfigProvider.defaultProvider.orElse(fromYamlString(readResourceFile("config.yml"))))
    .exitCode
