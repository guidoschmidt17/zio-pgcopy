package zio
package pgcopy

import io.netty.buffer.ByteBuf
import io.netty.buffer.PooledByteBufAllocator
import zio.Chunk
import zio.*

import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import scala.annotation.switch

import FrontendMessage.*
import Util.*
import Util.given

private sealed trait FrontendMessage:
  val payload: ByteBuf
  protected val buf: ByteBuf = PooledByteBufAllocator.DEFAULT.directBuffer(bytebufsize)
  protected def lengthPrefixed(i: Int, p: ByteBuf): ByteBuf =
    buf.setInt(i, p.writerIndex - i)

private object FrontendMessage:

  import Codec.*

  enum Variant:
    case Portal, Statement

  sealed trait UntaggedFrontendMessage extends FrontendMessage:
    buf.writeInt(Int.MinValue)
    def lengthPrefixed(p: ByteBuf): ByteBuf = lengthPrefixed(0, p)

  sealed trait TaggedFrontendMessage(tag: Char) extends FrontendMessage:
    buf.writeByte(tag.toByte)
    buf.writeInt(Int.MinValue)
    def lengthPrefixed(p: ByteBuf): ByteBuf = lengthPrefixed(1, p)

  abstract class EmptyFrontendMessage(tag: Char) extends TaggedFrontendMessage(tag):
    final val payload = lengthPrefixed(buf)

  case class StartupMessage(user: String, database: String) extends UntaggedFrontendMessage:
    inline private def keyed(k: String, v: String): ByteBuf =
      buf.writeUtf8z(k)
      buf.writeUtf8z(v)
    protected val message =
      buf.writeInt(196608)
      keyed("user", user)
      keyed("database", database)
      buf.writeByte(0)
    val payload = lengthPrefixed(message)

  case class SslStartupMessage() extends FrontendMessage:
    final val SslRequest = Array[Byte](0, 0, 0, 8, 4, -46, 22, 47)
    val payload = buf.writeBytes(SslRequest)

  case class PasswordMessage private (password: String) extends TaggedFrontendMessage('p'):
    val payload = lengthPrefixed(buf.writeUtf8z(password))
  object PasswordMessage:
    def cleartext(password: String) = PasswordMessage(password)
    def md5(user: String, password: String, salt: Array[Byte]) =
      val md5 = MessageDigest.getInstance("MD5")
      md5.update(password.getBytes(UTF_8))
      md5.update(user.getBytes(UTF_8))
      var hex = BigInt(1, md5.digest).toString(16)
      while hex.length < 32 do hex = "0" + hex
      md5.update(hex.getBytes(UTF_8))
      md5.update(salt)
      hex = BigInt(1, md5.digest).toString(16)
      while hex.length < 32 do hex = "0" + hex
      PasswordMessage(s"md5$hex")

  case class SASLInitialResponse(mechanism: String, response: Array[Byte]) extends TaggedFrontendMessage('p'):
    val payload =
      buf.writeUtf8z(mechanism)
      buf.writeInt(response.length)
      buf.writeBytes(response)
      lengthPrefixed(buf)

  case class SASLResponse(response: Array[Byte]) extends TaggedFrontendMessage('p'):
    val payload = lengthPrefixed(buf.writeBytes(response))

  case class Query(query: String) extends TaggedFrontendMessage('Q'):
    val payload = lengthPrefixed(buf.writeUtf8z(query))

  case class Parse(name: String, query: String) extends TaggedFrontendMessage('P'):
    val payload =
      buf.writeUtf8z(name)
      buf.writeUtf8z(query)
      buf.writeShort(0)
      lengthPrefixed(buf)

  case class Bind(name: String, fields: Int) extends TaggedFrontendMessage('B'):
    val payload =
      buf.writeUtf8z(name)
      buf.writeUtf8z(name)
      buf.writeShort(0)
      buf.writeShort(0)
      buf.writeShort(fields)
      Range(0, fields).foreach(_ => buf.writeShort(1))
      lengthPrefixed(buf)

  case class Execute(name: String, limit: Int) extends TaggedFrontendMessage('E'):
    val payload =
      buf.writeUtf8z(name)
      buf.writeInt(limit)
      lengthPrefixed(buf)

  sealed abstract class WithVariant(variant: Variant, name: String, tag: Char) extends TaggedFrontendMessage(tag):
    val payload =
      buf.writeByte((variant: @switch) match
        case Variant.Portal    => 'P'.toByte
        case Variant.Statement => 'S'.toByte
      )
      buf.writeUtf8z(name)
      lengthPrefixed(buf)

  case class Describe(variant: Variant, name: String) extends WithVariant(variant, name, 'D')

  case class Close(variant: Variant, name: String) extends WithVariant(variant, name, 'C')

  case class Flush() extends EmptyFrontendMessage('H')

  case class Sync() extends EmptyFrontendMessage('S')

  case class Terminate() extends EmptyFrontendMessage('X')

  case class CopyDone() extends EmptyFrontendMessage('c')

  case class CopyFail(reason: String) extends TaggedFrontendMessage('f'):
    val payload =
      buf.writeUtf8z(reason)
      lengthPrefixed(buf)

  case class CopyData[A](rows: Chunk[A])(using encoder: Encoder[A]) extends TaggedFrontendMessage('d'):
    override def toString = s"CopyOut(${rows.size})"
    val payload =
      given ByteBuf = buf
      val b = if checkbufsize then buf.capacity else 0
      buf.writeBytes(Header, 0, Header.length)
      rows.foreach(encoder(_))
      buf.writeShort(-1)
      if checkbufsize then if buf.capacity > b then println(s"warning: enlarge 'io.bytebufsize', $b -> ${buf.capacity}")
      lengthPrefixed(buf)

  private final val Header: Array[Byte] = "PGCOPY".getBytes.nn ++ Array(0x0a, 0xff, 0x0d, 0x0a, 0, 0, 0, 0, 0, 0, 0, 0, 0).map(_.toByte)

  private[pgcopy] final var ioConfig: IoConfig | Null = null

  private final lazy val bytebufsize = ioConfig.bytebufsize
  private final lazy val checkbufsize = ioConfig.checkbufsize
