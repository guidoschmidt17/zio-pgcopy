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
  val warmups = 1
  val p = 2
  val n = 400000
  val timeout = 120.seconds
  val begin = AtomicLong(0L)
  val elapsed = AtomicLong(0L)
  def lap = elapsed.set(nanoTime - begin.get)

  private def make(copy: Copy) = new Main:

    def run =
      import Simple.*
      for
        data <- randomSimples(n)
        warmup = for
          _ <- copy.in(in, ZStream.fromChunk(data))
          _ <- ZIO.scoped(copy.out(out, n).flatMap(_.runDrain))
        yield ()
        _ <- warmup.repeatN(warmups)
        _ = begin.set(nanoTime)
        i <- Random.nextIntBetween(1, 500)
        _ <- ZIO.sleep(i.milliseconds)
        loop = for
          _ <- copy.in(in, ZStream.fromChunk(data)).measured(s"copy.in")
          _ <- ZIO.scoped(copy.out(out, n).flatMap(_.runDrain).measured(s"copy.out"))
        yield lap
        _ <- loop.repeatN(repeats)
      yield ()

      // results: in: 13.5 / out: 7.8 / in/out: 6.5 (million ops/sec)

  val program = ZIO
    .service[Main]
    .provideSome[Scope](Main.layer, Copy.layer)
    .flatMap(ZIO.debug(s"warmup ...") *> _.run.forkDaemon.repeatN(sessions))
    .catchAllCause(ZIO.logErrorCause(_))
    *> ZIO.sleep(timeout) *> ZIO.debug(
      s"operations: ${p * n * (repeats + 1) * (sessions + 1)}, elapsed : ${elapsed.get / 1000000000d} sec, ${((p * n * (repeats + 1) * (sessions + 1)) / (elapsed.get / 1000000000d)).toLong} ops/sec"
    )

  val run = program
    .withRuntimeFlags(RuntimeFlags.disable(RuntimeFlag.FiberRoots))
    .withConfigProvider(ConfigProvider.defaultProvider.orElse(fromYamlString(readResourceFile("config.yml"))))
    .exitCode
