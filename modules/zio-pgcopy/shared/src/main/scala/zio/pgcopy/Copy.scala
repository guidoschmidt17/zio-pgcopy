package zio
package pgcopy

import zio.*
import zio.stream.*

trait Copy:

  import Copy.MakeError

  def in[E: MakeError, A: Encoder](insert: String, rows: ZStream[Any, E, A]): IO[E, Unit]

  def out[E: MakeError, A: Decoder](select: String, limit: Long = Long.MaxValue): ZIO[Scope, E, ZStream[Any, E, Chunk[A]]]

object Copy:

  type MakeError[E] = Any => E

  def layer[E: MakeError] = ZLayer.fromZIO(makeLayer)

  private def makeLayer[E: MakeError] =
    for
      pool <- ConnectionPool.make
      cfg <- ZIO.config(config)
    yield make(pool, cfg)

  private def make[E: MakeError](pool: ConnectionPool[E], config: Configuration) = new Copy:

    FrontendMessage.ioConfig = config.io

    def in[E: MakeError, A: Encoder](insert: String, instream: ZStream[Any, E, A]) =
      inline def copy(rows: Chunk[A])(using makeError: MakeError[E]) =
        ZIO
          .scoped(pool.get.flatMap(_.copyIn(insert, rows)))
          .catchAllDefect(ZIO.fail(_))
          .retry(pool.RetrySchedule("in"))
          .catchAll(e => ZIO.fail(makeError(e)))
      instream.chunks.tap(copy(_)).runDrain

    def out[E: MakeError, A: Decoder](select: String, limit: Long) =
      inline def copy(using makeError: MakeError[E]) =
        def loopResult(out: Queue[Take[E, Chunk[A]]], offsetRef: Ref[Long], n: Long) =
          def resultStream(offset: Long, limit: Long) =
            for
              connection <- pool.get
              stream <- connection.copyOut[A](s"$select offset $offset limit $limit")
            yield stream.tap(c => out.offer(Take.single(c)).flatMap(_ => offsetRef.update(_ + c.size)))
          for
            offset <- offsetRef.get
            result <- resultStream(offset, n - offset)
            _ <- result.runDrain
          yield ()
        for
          offset: Ref[Long] <- Ref.make(0L)
          out: Queue[Take[E, Chunk[A]]] <- Queue.bounded(config.io.outgoingsize)
          outstream = ZStream
            .fromQueue(out, out.capacity)
            .flattenTake
          _ <- loopResult(out, offset, limit)
            .catchAllCause(c => ZIO.logErrorCause(c) *> ZIO.fail(c))
            .retry(pool.RetrySchedule(s"copy.out"))
            .catchAll(e => ZIO.fail(makeError(e)))
            .ensuring(out.offer(Take.end))
            .forkScoped
        yield outstream
      copy

    private def describe[E: MakeError, A](select: String) =
      inline def prepare(using makeError: MakeError[E]) =
        ZIO
          .scoped(pool.get.flatMap(_.describe(select)))
          .catchAll(e => ZIO.fail(makeError(e)))
      prepare

  private[pgcopy] final val config: Config[Configuration] =
    val host = Config.string("host").withDefault("localhost")
    val port = Config.int("port").withDefault(5432)
    val sslmode = Config
      .string("sslmode")
      .withDefault("disable")
      .validate("sslmode must be one of [disable, trust, runtime]")(s => Set("disable", "trust", "runtime").contains(s.toLowerCase))
      .map(_.toLowerCase.nn)
      .map(_.capitalize)
      .map(ConnectionPool.Ssl.Mode.valueOf(_))
    val database = Config.string("database").withDefault("world")
    val user = Config.string("user").withDefault("jimmy")
    val password = Config.string("password").withDefault("banana")
    val server =
      (host ++ port ++ sslmode ++ database ++ user ++ password).map((a, b, c, d, e, f) => ServerConfig(a, b, c, d, e, f)).nested("server")

    val min = Config.int("min").withDefault(0).validate("Min poolsize must be >= 0")(_ >= 0)
    val max = Config.int("max").withDefault(64).validate("Max poolsize must be >= 1")(_ >= 1)
    val timeout = Config.duration("timeout").withDefault(15.minutes).validate("Pool timeout must be >= 90 seconds")(_ >= 90.seconds)
    val pool = (min ++ max ++ timeout).map((a, b, c) => PoolConfig(a, b, c)).nested("pool")

    val base = Config.duration("base").withDefault(100.milliseconds)
    val factor = Config.double("factor").withDefault(1.25d)
    val retries = Config.int("retries").withDefault(24)
    val retry = (base ++ factor ++ retries).map((a, b, c) => RetryConfig(a, b, c)).nested(("retry"))

    val so_sndbuf = Config.int("so_sndbuf").map(Util.ceilPower2(_)).withDefault(32 * 1024)
    val so_rcvbuf = Config.int("so_rcvbuf").map(Util.ceilPower2(_)).withDefault(32 * 1024)
    val bytebufsize = Config.int("bytebufsize").map(Util.ceilPower2(_)).withDefault(128 * 1024)
    val checkbufsize = Config.boolean("checkbufsize").withDefault(false)
    val incomingsize = Config.int("incomingsize").map(Util.ceilPower2(_)).withDefault(8 * 1024)
    val outgoingsize = Config.int("outgoingsize").map(Util.ceilPower2(_)).withDefault(4 * 1024)
    val io = (so_rcvbuf ++ so_sndbuf ++ bytebufsize ++ checkbufsize ++ incomingsize ++ outgoingsize)
      .map((a, b, c, d, e, f) => IoConfig(a, b, c, d, e, f))
      .nested(("io"))

    (server ++ pool ++ retry ++ io).nested("zio-pgcopy").map((a, b, c, d) => Configuration(a, b, c, d))

private case class ServerConfig(
    host: String,
    port: Int,
    sslmode: ConnectionPool.Ssl.Mode,
    database: String,
    user: String,
    password: String
)
private case class PoolConfig(min: Int, max: Int, timeout: Duration)
private case class RetryConfig(base: Duration, factor: Double, retries: Int)
private case class IoConfig(so_rcvbuf: Int, so_sndbuf: Int, bytebufsize: Int, checkbufsize: Boolean, incomingsize: Int, outgoingsize: Int)
private case class Configuration(server: ServerConfig, pool: PoolConfig, retry: RetryConfig, io: IoConfig)
