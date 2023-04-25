package simple

import zio.*
import zio.config.yaml.YamlConfigProvider.fromYamlString
import zio.pgcopy.*
import zio.stream.*

import java.lang.System.nanoTime
import java.util.concurrent.atomic.AtomicLong

trait Main:
  def run: ZIO[Scope, Any, Unit]

object Main extends ZIOAppDefault:

  lazy val layer = ZLayer.fromFunction(make)

  given MakeError[String] = _.toString

  val sessions = 19
  val repeats = 29
  val warmups = 9
  val n = 400000
  val timeout = 120.seconds
  val begin = AtomicLong(0)
  val elapsed = AtomicLong(0L)
  def lap = elapsed.set(nanoTime - begin.get)
  val in = Codec.in[Simple]
  val out = Codec.out[Simple]

  private def make(copy: Copy) = new Main:

    def run =
      import Simple.*
      val warmup = for
        f <- randomSimples(n)
        _ <- copy.in(in, ZStream.fromChunk(f)).measured(s"copy.in")
        _ <- ZIO.scoped(copy.out[String, Simple](out, n).flatMap(_.runDrain))
        i <- Random.nextIntBetween(1, 500)
        _ <- ZIO.sleep(i.milliseconds)
      yield ()
      warmup.repeatN(warmups)
      begin.set(nanoTime)
      val loop = for
        f <- randomSimples(n)
        _ <- copy.in(in, ZStream.fromChunk(f)).measured(s"copy.in")
        _ <- ZIO.scoped(copy.out[String, Simple](out, n).flatMap(_.runDrain).measured(s"copy.out"))
      yield lap
      loop.repeatN(repeats)

  val program = ZIO
    .service[Main]
    .provideSome[Scope](Main.layer, Copy.layer)
    .flatMap(_.run.forkDaemon.repeatN(sessions))
    .catchAllCause(ZIO.logErrorCause(_))
    *> ZIO.sleep(timeout) *> ZIO.debug(
      s"operations: ${2 * n * (repeats + 1) * (sessions + 1)}, elapsed : ${elapsed.get / 1000000000d} sec, ${((2 * n * (repeats + 1) * (sessions + 1)) / (elapsed.get / 1000000000d)).toLong} ops/sec"
    )

  val run = program
    .withRuntimeFlags(RuntimeFlags.disable(RuntimeFlag.FiberRoots))
    .withConfigProvider(ConfigProvider.defaultProvider.orElse(fromYamlString(readResourceFile("config.yml"))))
    .exitCode
