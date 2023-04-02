package zio
package pgcopy

import zio.*
import zio.stream.*

trait PostgresCopy:

  import PostgresCopy.MakeError

  def copyIn[E: MakeError, A: Encoder](insert: String, rows: ZStream[Any, E, A], sizeHintPerRow: Int): IO[E, Unit]

  def copyOut[E: MakeError, A: Decoder](select: String, limit: Long = Long.MaxValue): ZIO[Scope, E, ZStream[Any, E, Chunk[A]]]

  def describeCopyOut[E: MakeError, A](select: String): IO[E, Unit]

object PostgresCopy:

  type MakeError[E] = Any => E

  def layer[E: MakeError] = ZLayer.fromZIO(makeLayer)

  private def makeLayer[E: MakeError] =
    for
      pool <- ConnectionPool.make
      cfg <- ZIO.config(config)
    yield make(pool, cfg)

  private def make[E: MakeError](pool: ConnectionPool[E], config: Configuration) = new PostgresCopy:

    FrontendMessage.ByteBufInitialSize = config.io.bytebufinitial

    def copyIn[E: MakeError, A: Encoder](insert: String, instream: ZStream[Any, E, A], sizeHintPerRow: Int) =
      inline def copy(rows: Chunk[A])(using makeError: MakeError[E]) =
        ZIO
          .scoped(pool.get.flatMap(_.copyIn(insert, rows, sizeHintPerRow)))
          .catchAllDefect(ZIO.fail(_))
          .retry(pool.RetrySchedule)
          .catchAll(e => ZIO.fail(makeError(e)))
      instream.chunks.tap(copy(_)).runDrain

    def copyOut[E: MakeError, A: Decoder](select: String, limit: Long) =
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
        out: Queue[Take[E, Chunk[A]]] <- Queue.bounded(config.io.outgpingstream)
        outstream = ZStream
          .fromQueue(out, out.capacity)
          .flattenTake
        _ <- loopResult(out, offset, limit)
          .catchAllDefect(ZIO.fail(_))
          .retry(pool.RetrySchedule)
          .ensuring(out.offer(Take.end))
          .forkScoped
      yield outstream

    def describeCopyOut[E: MakeError, A](select: String) =
      inline def prepare(using makeError: MakeError[E]) =
        ZIO
          .scoped(pool.get.flatMap(_.prepare(select)))
          .catchAll(e => ZIO.fail(makeError(e)))
      prepare

    private def copyInChunk[E: MakeError, A: Encoder](insert: String, rows: Chunk[A], sizeHintPerRow: Int) =
      inline def copy(using makeError: MakeError[E]) =
        ZIO
          .scoped(pool.get.flatMap(_.copyIn(insert, rows, sizeHintPerRow)))
          .catchAllDefect(ZIO.fail(_))
          .retryN(1)
          .catchAll(e => ZIO.fail(makeError(e)))
      copy

  private[pgcopy] case class ServerConfig(
      host: String,
      port: Int,
      sslmode: ConnectionPool.Ssl.Mode,
      database: String,
      user: String,
      password: String
  )
  private[pgcopy] case class PoolConfig(min: Int, max: Int, timeout: Duration)
  private[pgcopy] case class RetryConfig(base: Duration, factor: Double, retries: Int)
  private[pgcopy] case class IoConfig(sockerbuffer: Int, bytebufinitial: Int, incomingqueue: Int, outgpingstream: Int)
  private[pgcopy] case class Configuration(server: ServerConfig, pool: PoolConfig, retry: RetryConfig, io: IoConfig)

  private[pgcopy] final val config: Config[Configuration] =
    val host = Config.string("host").withDefault("localhost")
    val port = Config.int("port").withDefault(5432)
    val sslmode = Config
      .string("sslmode")
      .withDefault("disable")
      .validate("sslmode must be one of [disable, trust, runtime]")(s => Set("disable", "trust", "runtime").contains(s.toLowerCase))
    val database = Config.string("database").withDefault("world")
    val user = Config.string("user").withDefault("jimmy")
    val password = Config.string("password").withDefault("banana")
    val server = (host ++ port ++ sslmode ++ database ++ user ++ password)
      .map((h, p, s, d, u, pw) => ServerConfig(h, p, ConnectionPool.Ssl.Mode.valueOf(s.toLowerCase.nn.capitalize), d, u, pw))
      .nested("server")
    val min = Config.int("min").withDefault(0).validate("Min poolsize must be >= 0")(_ >= 0)
    val max = Config.int("max").withDefault(64).validate("Max poolsize must be >= 1")(_ >= 1)
    val timeout = Config.duration("timeout").withDefault(90.seconds).validate("Pool timeout must be >= 15 seconds")(_ >= 15.seconds)
    val pool = (min ++ max ++ timeout).map((mn, mx, tm) => PoolConfig(mn, mx, tm)).nested("pool")
    val base = Config.duration("base").withDefault(100.milliseconds)
    val factor = Config.double("factor").withDefault(1.25d)
    val retries = Config.int("retries").withDefault(24)
    val retry = (base ++ factor ++ retries).map((b, f, r) => RetryConfig(b, f, r)).nested(("retry"))
    val socketbuffer = Config.int("socketbuffer").withDefault(8 * 1024 * 1024)
    val bytebufinitial = Config.int("bytebufinitial").withDefault(128 * 1024)
    val incomingqueue = Config.int("incomingqueue").withDefault(8 * 1024)
    val outgoingstream = Config.int("outgoingstream").withDefault(4 * 1024)
    val io = (socketbuffer ++ bytebufinitial ++ incomingqueue ++ outgoingstream).map((s, b, i, o) => IoConfig(s, b, i, o)).nested(("io"))
    (server ++ pool ++ retry ++ io).nested("zio-pgcopy").map((s, p, r, i) => Configuration(s, p, r, i))
