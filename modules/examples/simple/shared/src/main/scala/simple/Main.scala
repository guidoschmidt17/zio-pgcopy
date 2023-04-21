package simple

import zio.*
import zio.config.yaml.YamlConfigProvider.fromYamlString
import zio.pgcopy.*
import zio.stream.*

trait Main:
  def run: ZIO[Scope, Any, Unit]

object Main extends ZIOAppDefault:

  lazy val layer = ZLayer.fromFunction(make)

  given MakeError[String] = _.toString

  val sessions = 19
  val repeats = 29

  private def make(copy: Copy) = new Main:
    def run =
      import Simple.*
      val n = 100000
      val in = s"simple(i)"
      val out = s"select i from simple"
      val loop = for
        i <- Random.nextIntBetween(5, 100)
        _ <- ZIO.sleep(i.milliseconds)
        // f <- randomSimples(n)
        // _ <- copy.in(in, ZStream.fromChunk(f)).measured(s"copy.in")
        _ <- ZIO.scoped(copy.out[String, Simple](out, n).flatMap(_.runDrain).measured(s"copy.out"))
      yield ()
      loop.repeatN(repeats)

  val program = ZIO
    .service[Main]
    .provideSome[Scope](Main.layer, Copy.layer)
    .flatMap(_.run.forkDaemon.repeatN(sessions))
    .catchAllCause(ZIO.logErrorCause(_))
    *> ZIO.sleep(30.seconds) *> ZIO.debug(s"shutdown")

  val run = program
    .withRuntimeFlags(RuntimeFlags.disable(RuntimeFlag.FiberRoots))
    .withConfigProvider(ConfigProvider.defaultProvider.orElse(fromYamlString(readResourceFile("config.yml"))))
    .exitCode
