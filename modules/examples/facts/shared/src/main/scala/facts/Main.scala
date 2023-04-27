package facts

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

  val sessions = 7
  val repeats = 29
  val warmups = 1
  val p = 2
  val n = 100000
  val timeout = 60.seconds
  val begin = AtomicLong(0)
  val elapsed = AtomicLong(0L)
  def lap = elapsed.set(nanoTime - begin.get)

  private def make(copy: Copy) = new Main:

    def run =
      import Fact.*
      for
        data <- randomFacts(n)
        warmup = for
          _ <- copy.in(in, ZStream.fromChunk(data).rechunk(32 * 1024))
          _ <- ZIO.scoped(copy.out[String, Narrow.Fact](out, n).flatMap(_.runDrain))
        yield ()
        _ <- warmup.repeatN(warmups)
        _ = begin.set(nanoTime)
        i <- Random.nextIntBetween(1, 500)
        _ <- ZIO.sleep(i.milliseconds)
        loop = for
          _ <- copy.in(in, ZStream.fromChunk(data).rechunk(32 * 1024)).measured(s"copy.in")
          _ <- ZIO.scoped(copy.out[String, Narrow.Fact](out, n).flatMap(_.runDrain).measured(s"copy.out"))
        yield lap
        _ <- loop.repeatN(repeats)
      yield ()

      // results: in: 0.9 / out: 2.3 / in/out: 1.4 (mio ops/sec)

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
