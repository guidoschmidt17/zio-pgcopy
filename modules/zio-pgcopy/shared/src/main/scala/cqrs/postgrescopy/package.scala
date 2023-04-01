import io.netty.buffer.ByteBuf
import zio.Random.nextUUID
import zio.*

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID
import scala.quoted.*

package object postgrescopy:

  extension (buf: ByteBuf)
    def readUtf8(len: Int): String =
      String.valueOf(buf.readCharSequence(math.min(len, buf.readableBytes), UTF_8))
    def readUtf8z: String =
      var i = buf.readerIndex
      while buf.getByte(i) != 0 do i += 1
      val res = String.valueOf(buf.readCharSequence(i - buf.readerIndex, UTF_8))
      buf.readByte
      res
    def readByteArray(len: Int): Array[Byte] =
      val arr = Array.ofDim[Byte](math.min(len, buf.readableBytes))
      buf.readBytes(arr)
      arr
    def readRemaining: Array[Byte] =
      buf.readByteArray(buf.readableBytes)
    def readIgnore(len: Int): Unit =
      buf.readerIndex(buf.readerIndex + len)
    def testUtf8(s: String): Boolean =
      if buf.readableBytes < s.length then false
      else s == String.valueOf(buf.getCharSequence(buf.readerIndex, s.length, UTF_8))
    def writeUtf8(s: String): ByteBuf =
      buf.writeBytes(s.getBytes(UTF_8))
    def writeUtf8z(s: String): ByteBuf =
      writeUtf8(s)
      buf.writeByte(0)

  inline given nn_conversion[A]: Conversion[A | Null, A] = _.nn

  case class Uuid private (uuid: UUID) extends AnyVal:

    override def toString = uuid.toString

    inline def toBytes: Array[Byte] =
      val bb = ByteBuffer.wrap(Array.ofDim[Byte](16))
      bb.putLong(uuid.getMostSignificantBits)
      bb.putLong(uuid.getLeastSignificantBits)
      bb.array

  object Uuid:

    def apply(uuid: UUID): Uuid = new Uuid(uuid)

    inline def apply(s: String): Uuid = fromString(s)

    inline def fromString(s: String): Uuid = Uuid(UUID.fromString(s.toLowerCase))

    inline def fromBytes(bytes: Array[Byte]): Uuid =
      val bb = ByteBuffer.wrap(bytes)
      val most = bb.getLong
      val least = bb.getLong
      Uuid(new UUID(most, least))

  object Field:

    inline def fieldsCount[A]: Short = getFields[A].length.toShort

    inline def fieldsExpression[A]: String = getFields[A].map(_.toLowerCase).mkString(",")

    inline def getFields[A]: Seq[String] = ${ getFieldsImpl[A] }

    private def getFieldsImpl[A](using quotes: Quotes, tpe: Type[A]): Expr[Seq[String]] =
      import quotes.reflect.*
      val sym = TypeTree.of[A].symbol
      val names = sym.caseFields.map(_.name)
      val namesExpr: Expr[Seq[String]] = Expr.ofSeq(names.map(Expr(_)))
      '{ $namesExpr.toSeq }
