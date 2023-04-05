package zio
package pgcopy

import com.ongres.scram.client.*
import com.ongres.scram.common.stringprep.StringPreparations
import io.netty.bootstrap.*
import io.netty.buffer.*
import io.netty.channel.*
import io.netty.channel.nio.*
import io.netty.channel.socket.*
import io.netty.channel.socket.nio.*
import io.netty.handler.codec.*
import io.netty.handler.flush.*
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import zio.*
import zio.stream.*

import java.nio.charset.StandardCharsets.UTF_8
import java.security.KeyStore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.TrustManagerFactory
import scala.annotation.switch

import BackendMessage.*
import Copy.*
import FrontendMessage.*
import ConnectionPool.*
import Connection.Status
import Status.*
import Codec.*

private case class Connection[E: MakeError](
    private val future: ChannelFuture,
    private val incoming: Incoming,
    private val pool: ConnectionPool[E],
    private val config: Configuration
):

  def copyOut[A: Decoder](query: String)(using makeError: MakeError[E]) =
    for
      _ <- send(Query(s"copy ($query) to stdout with binary"))
      _ <- receiveUntil(CopyingOut)
    yield instream
      .map(handleCopyOut(_))
      .flattenTake
      .rechunk(incoming.capacity)
      .chunks
      .concat(if isClosed then ZStream.fail(makeError(s"broken pipe $this")) else ZStream.empty)

  def copyIn[A: Encoder](tableexpression: String, rows: Chunk[A]) =
    for
      _ <- send(Query(s"copy $tableexpression from stdout with binary"))
      _ <- receiveUntil(CopyingIn)
      _ <- send(FrontendMessage.CopyData(rows))
      _ <- send(FrontendMessage.CopyDone())
      _ <- receiveUntil(Idle)
    yield ()

  def describe[A](query: String) =
    for
      stmt <- ZIO.succeed("")
      _ <- send(Parse(stmt, query + s" offset ${Long.MaxValue} limit 0"))
      _ <- send(Describe(Variant.Statement, stmt))
      _ <- send(Bind(stmt, 0))
      _ <- send(Close(Variant.Statement, stmt))
      _ <- send(Execute(stmt, 0))
      _ <- send(Sync())
      _ <- receiveUntil(Idle)
    yield ()

  override def toString = s"Connection(0x${channel}, $getStatus)"

  def isNew = getStatus == NotConnected
  def isOpen = getStatus != Closed
  def isClosed = getStatus == Closed

  private[pgcopy] def close: IO[E, Unit] =
    if isOpen then
      for
        _ <- pool.invalidate(this)
        _ <- incoming.shutdown
        _ <- closeChannel.ignore
        _ <- setStatus(Closed)
      yield ()
    else ZIO.unit

  private[pgcopy] def handle(message: BackendMessage): IO[E, Unit] =
    if isOpen then incoming.offer(message).unit else incoming.shutdown

  private def startup: IO[E, Unit] =
    import config.server.*
    if status.compareAndSet(NotConnected, Connecting) then
      for
        _ <- send(StartupMessage(user, database))
        _ <- receiveUntil(Connected)
      yield ()
    else ZIO.unit

  private def closeChannel =
    ZIO.attempt {
      if channel != null && channel.isActive then
        channel.config.setAutoRead(false)
        channel.close.sync
    }

  private def send(message: FrontendMessage)(using makeError: MakeError[E]): IO[E, Unit] =
    startup *> ZIO
      .attempt(channel.writeAndFlush(message.payload).sync)
      .catchAllDefect(ZIO.fail(_))
      .catchAll(e => ZIO.fail(makeError(e)))
      .unit

  private def receiveUntil(status: Status): IO[E, Unit] =
    instream.runForeachWhile(_ => ZIO.succeed(getStatus != status && isOpen))

  private def handleCopyOut[A: Decoder](message: BackendMessage)(using makeError: MakeError[E]): Take[E, A] =
    import BackendMessage.*
    inline def decode(data: ByteBuf)(using decoder: Decoder[A]) =
      given ByteBuf = data
      try Take.single(decoder())
      finally data.release(1)
    message match
      case CopyData(_, data) if isNotHeader               => decode(data)
      case CopyData(_, data)                              => data.ignoreCopyOutHeader; isNotHeader = true; decode(data)
      case CopyDataFooter | CopyDone | CommandComplete(_) => Take.chunk(Chunk.empty)
      case ReadyForQuery(i) if i == 'I'                   => Take.end
      case _                                              => Take.fail(makeError(message))

  private def handleMessage(message: BackendMessage)(using makeError: MakeError[E]): IO[E, Unit] =
    import config.server.user
    import config.server.password
    // if !message.isInstanceOf[BackendMessage.CopyData] then println(s"$message")
    message match
      case CopyOutResponse(_, _, _)         => isNotHeader = false; setStatus(CopyingOut)
      case CopyInResponse(_, _, _)          => setStatus(CopyingIn)
      case CommandComplete(_)               => setStatus(CommandCommpleted)
      case CloseComplete                    => setStatus(Prepared)
      case ErrorResponse(errors)            => ZIO.debug(s"$errors") *> ZIO.fail(makeError(errors))
      case ParameterStatus(name, value)     => ZIO.succeed { parameters += name -> value }
      case BackendKeyData(pid, secret)      => ZIO.succeed { keydata = (pid, secret) }
      case AuthenticationOk                 => setStatus(Connected)
      case AuthenticationClearTextPassword  => setStatus(Connecting) *> send(PasswordMessage.cleartext(password))
      case AuthenticationMD5Password(salt)  => setStatus(Connecting) *> send(PasswordMessage.md5(user, password, salt))
      case AuthenticationSASL(mechanisms)   => initialSASL(mechanisms)
      case AuthenticationSASLContinue(data) => continueSASL(data, password)
      case AuthenticationSASLFinal(data)    => finalSASL(data)
      case ReadyForQuery(i) =>
        indicator = i
        setStatus((i: @switch) match
          case 'I' => Idle
          case 'T' => NotIdle
          case 'E' => Failed
        )
      case _ => ZIO.unit

  private def getStatus: Status = status.get

  private def setStatus(s: Status): UIO[Unit] =
    ZIO.succeed(status.set(s))

  private def initialSASL(mechanisms: Seq[String]) =
    val client = ScramClient
      .channelBinding(ScramClient.ChannelBinding.NO)
      .stringPreparation(StringPreparations.SASL_PREPARATION)
      .selectMechanismBasedOnServerAdvertised(mechanisms*)
      .setup
    scramsession = client.scramSession("*")
    for
      _ <- setStatus(Connecting)
      _ <- send(SASLInitialResponse(client.getScramMechanism.getName, scramsession.clientFirstMessage.getBytes(UTF_8)))
    yield ()

  private def continueSASL(data: Array[Byte], password: String) =
    val server = scramsession.receiveServerFirstMessage(String(data, UTF_8))
    scramclientfinal = server.clientFinalProcessor(password)
    send(SASLResponse(scramclientfinal.clientFinalMessage.getBytes(UTF_8)))

  private def finalSASL(data: Array[Byte]) =
    ZIO.succeed(scramclientfinal.receiveServerFinalMessage(String(data, UTF_8)))

  private lazy val instream = ZStream
    .fromQueue(incoming, incoming.capacity)
    .tap(handleMessage(_))

  private lazy val channel: Channel | Null = future.channel

  private final val status = AtomicReference[Status](NotConnected)
  private final var parameters = Map.empty[String, String]
  private final var keydata: (Int, Int) = (0, 0)
  private final var indicator = 0.toChar
  private final var scramsession: ScramSession | Null = null
  private final var scramclientfinal: ScramSession#ClientFinalProcessor | Null = null
  private final var isNotHeader = true

private object Connection:

  enum Status:
    case NotConnected, Connecting, Connected, Idle, NotIdle, Failed, Prepared, CopyingOut, CopyingIn, CommandCommpleted, Closed

private case class ConnectionPool[E: MakeError] private (
    private val zpool: Ref[ZPool[E, Connection[E]] | Null],
    private val bootstrap: Bootstrap,
    private val config: Configuration
):
  import ConnectionPool.*
  import Copy.MakeError
  import config.retry.*
  import config.io.*

  def get: ZIO[Scope, E, Connection[E]] =
    zpool.get.flatMap(_.get)

  def invalidate(connection: Connection[E]) =
    zpool.get.flatMap(_.invalidate(connection))

  private def acquire(using makeError: MakeError[E]): IO[E, Connection[E]] =
    val loop = for
      incoming: Incoming <- Queue.bounded(incomingsize)
      channelfuture = bootstrap.connect
      connection = Connection[E](channelfuture, incoming, this, config)
      _ = channelfuture.sync.channel.pipeline.addLast(ProtocolHandler(connection))
    yield connection
    loop
      .catchAllDefect(e => ZIO.debug(e) *> ZIO.fail(e))
      .retry(RetrySchedule)
      .catchAll(e => ZIO.fail(makeError(e)))

  private def release[E](connection: Connection[E]): UIO[Unit] =
    connection.close.ignore

  private[pgcopy] final val RetrySchedule =
    Schedule.exponential(base, factor) && Schedule
      .recurs(math.max(0, retries - 1))
      .onDecision((state, out, decision) =>
        decision match
          case Schedule.Decision.Continue(intervals) => ZIO.debug(s"pgcopy/connectionpool : retries $state/${retries} ")
          case Schedule.Decision.Done                => ZIO.debug(s"pgcopy/connectionpool : retries failed $state/${retries} ")
      )

private object ConnectionPool:

  def make[E: MakeError] =
    for
      ref: Ref[ZPool[E, Connection[E]] | Null] <- Ref.make(null)
      config <- ZIO.config(Copy.config)
      pool <- ZIO.succeed(ConnectionPool(ref, bootstrap(config), config))
      get = ZIO.acquireRelease(pool.acquire)(pool.release)
      zpool <- ZPool.make(get, Range.inclusive(config.pool.min, config.pool.max), config.pool.timeout)
      _ <- ref.setAsync(zpool)
    yield pool

  type Incoming = Queue[BackendMessage]
  type InStream[E] = ZStream[Any, E, BackendMessage]
  type Outgoing[E, A] = Queue[Take[E, A]]

  object Ssl:
    def apply(mode: Mode): Option[TrustManagerFactory] =
      (mode: @switch) match
        case Mode.Disable => None
        case Mode.Trust   => Some(insecure)
        case Mode.Runtime => Some(runtime)
    final private lazy val insecure = InsecureTrustManagerFactory.INSTANCE
    final private lazy val runtime =
      val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
      factory.init(null.asInstanceOf[KeyStore])
      factory
    enum Mode:
      case Disable, Trust, Runtime

  private final class SslStartupHandler(factory: TrustManagerFactory, host: String, port: Int) extends SimpleChannelInboundHandler[ByteBuf]:

    override def channelActive(ctx: ChannelHandlerContext): Unit =
      ctx.writeAndFlush(SslStartupMessage().payload)

    override def channelRead0(ctx: ChannelHandlerContext, buf: ByteBuf): Unit =
      val indicator = buf.readByte.toChar
      val channel = ctx.channel.nn
      val pipeline = channel.pipeline.nn
      (indicator: @switch) match
        case 'S' =>
          val channel = ctx.channel.nn
          val sslcontext = SslContextBuilder.forClient.trustManager(factory).build
          pipeline.removeFirst
          pipeline.addFirst(
            sslcontext.newHandler(channel.alloc, host, port),
            FlushConsolidationHandler(),
            LengthFieldBasedFrameDecoder(Int.MaxValue, 1, 4, -4, 0)
          )
        case 'N' =>
          ctx.close
          throw RuntimeException(s"Postgresql server not configured for SSL : '$indicator'")
        case _ =>
          ctx.close
          throw RuntimeException(s"Invalid character received during SSL negotation : '$indicator'")

  private final class ProtocolHandler[E](connection: Connection[E]) extends SimpleChannelInboundHandler[ByteBuf]:

    inline override def channelRead0(ctx: ChannelHandlerContext, buf: ByteBuf): Unit =
      given ByteBuf = buf
      Unsafe.unsafely(Runtime.default.unsafe.run(connection.handle(BackendMessage())))

    override def channelInactive(ctx: ChannelHandlerContext): Unit =
      invalidate(ctx)

    override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit =
      invalidate(ctx)

    private def invalidate(ctx: ChannelHandlerContext) =
      Unsafe.unsafely(Runtime.default.unsafe.run(connection.close))

  private def bootstrap(config: Configuration): Bootstrap =
    import config.server.*
    import config.io.*
    val initializer = new ChannelInitializer[NioSocketChannel]:
      def initChannel(channel: NioSocketChannel) =
        Ssl(sslmode) match
          case Some(factory) =>
            channel.pipeline.addLast(SslStartupHandler(factory, host, port))
          case None =>
            channel.pipeline.addLast(FlushConsolidationHandler(), LengthFieldBasedFrameDecoder(Int.MaxValue, 1, 4, -4, 0))
    Bootstrap()
      .group(NioEventLoopGroup())
      .channel(classOf[NioSocketChannel])
      .option(ChannelOption.TCP_NODELAY, true)
      .option(ChannelOption.SO_RCVBUF, so_rcvbuf)
      .option(ChannelOption.SO_SNDBUF, so_sndbuf)
      .handler(initializer)
      .remoteAddress(host, port)
