package zio
package pgcopy

import io.netty.buffer.ByteBuf

import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import scala.annotation.switch
import scala.annotation.targetName
import scala.reflect.ClassTag

trait Decoder[A]:
  def apply()(using ByteBuf): A
trait ArrayDecoder[A] extends Decoder[Array[A]]:
  def apply()(using ByteBuf): Array[A]
trait Encoder[A]:
  def apply(a: A)(using ByteBuf): Unit
trait ArrayEncoder[A] extends Encoder[Array[A]]:
  def apply(a: Array[A])(using ByteBuf): Unit
trait Codec[A] extends Encoder[A], Decoder[A]
trait ArrayCodec[A] extends ArrayEncoder[A], ArrayDecoder[A]

object Codec:

  import util.*

  protected sealed trait BaseEncoder[A] extends Encoder[A]:
    final lazy val typeoid: Int = Types.get(this).get
  protected sealed trait BaseArrayEncoder[A] extends ArrayEncoder[A]:
    final lazy val typeoid: Int = Types.get(this).get
  protected sealed trait BaseCodec[A] extends Codec[A], BaseEncoder[A]
  protected sealed trait BaseArrayCodec[A] extends ArrayCodec[A], BaseArrayEncoder[A]

  private class ArrayBuilder[A: ClassTag]:
    def apply(decoder: Decoder[A])(using buf: ByteBuf): Array[A] =
      buf.ignore(16)
      val len = buf.readInt
      buf.ignoreInt
      val arr = Array.ofDim[A](len)
      Range(0, len).foreach(i => arr.update(i, decoder()))
      arr
    def apply(a: Array[A], encoder: BaseEncoder[A])(using buf: ByteBuf): Unit =
      val start = buf.writerIndex
      buf.writeBytes(Array[Byte](0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0), 0, 12)
      buf.writeInt(encoder.typeoid)
      buf.writeInt(a.length)
      buf.writeInt(0)
      a.foreach(encoder(_))
      buf.setInt(start, buf.writerIndex - start - 4)

  private object ArrayBuilder:
    def apply[A: ClassTag](decoder: Decoder[A])(using ByteBuf): Array[A] = new ArrayBuilder[A].apply(decoder)
    def apply[A: ClassTag](a: Array[A], encoder: BaseEncoder[A])(using ByteBuf): Unit = new ArrayBuilder[A].apply(a, encoder)

  object fields extends Codec[Short]:
    def apply()(using buf: ByteBuf) =
      buf.readShort
    def apply(a: Short)(using buf: ByteBuf) =
      buf.writeShort(a)
  object int2 extends BaseCodec[Short]:
    def apply()(using buf: ByteBuf) =
      buf.ignoreInt
      buf.readShort
    def apply(a: Short)(using buf: ByteBuf) =
      buf.writeInt(2)
      buf.writeShort(a)
  object int4 extends BaseCodec[Int]:
    def apply()(using buf: ByteBuf) =
      buf.ignoreInt
      buf.readInt
    def apply(a: Int)(using buf: ByteBuf) =
      buf.writeInt(4)
      buf.writeInt(a)
  object int8 extends BaseCodec[Long]:
    def apply()(using buf: ByteBuf) =
      buf.ignoreInt
      buf.readLong
    def apply(a: Long)(using buf: ByteBuf) =
      buf.writeInt(8)
      buf.writeLong(a)
  object float4 extends BaseCodec[Float]:
    def apply()(using buf: ByteBuf) =
      buf.ignoreInt
      buf.readFloat
    def apply(a: Float)(using buf: ByteBuf) =
      buf.writeInt(4)
      buf.writeFloat(a)
  object float8 extends BaseCodec[Double]:
    def apply()(using buf: ByteBuf) =
      buf.ignoreInt
      buf.readDouble
    def apply(a: Double)(using buf: ByteBuf) =
      buf.writeInt(8)
      buf.writeDouble(a)
  object numeric extends BaseCodec[BigDecimal]:
    def apply()(using ByteBuf) =
      BigDecimal(text())
    def apply(a: BigDecimal)(using buf: ByteBuf) =
      buf.writeUtf8z(s"'$a'::numeric")

  object _int2 extends BaseArrayCodec[Short]:
    def apply()(using ByteBuf) = ArrayBuilder[Short](int2)
    def apply(a: Array[Short])(using ByteBuf) = ArrayBuilder[Short](a, int2)
  object _int4 extends BaseArrayCodec[Int]:
    def apply()(using ByteBuf) = ArrayBuilder[Int](int4)
    def apply(a: Array[Int])(using ByteBuf) = ArrayBuilder[Int](a, int4)
  object _int8 extends BaseArrayCodec[Long]:
    def apply()(using ByteBuf) = ArrayBuilder[Long](int8)
    def apply(a: Array[Long])(using ByteBuf) = ArrayBuilder[Long](a, int8)
  object _float4 extends BaseArrayCodec[Float]:
    def apply()(using ByteBuf) = ArrayBuilder[Float](float4)
    def apply(a: Array[Float])(using ByteBuf) = ArrayBuilder[Float](a, float4)
  object _float8 extends BaseArrayCodec[Double]:
    def apply()(using ByteBuf) = ArrayBuilder[Double](float8)
    def apply(a: Array[Double])(using ByteBuf) = ArrayBuilder[Double](a, float8)
  object _numeric extends BaseArrayCodec[BigDecimal]:
    def apply()(using ByteBuf) = ArrayBuilder[BigDecimal](numeric)
    def apply(a: Array[BigDecimal])(using ByteBuf) = ArrayBuilder[BigDecimal](a, numeric)

  object text extends BaseCodec[String]:
    def apply()(using buf: ByteBuf) =
      buf.readUtf8(buf.readInt)
    def apply(a: String)(using buf: ByteBuf) =
      buf.writeInt(a.length)
      buf.writeUtf8(a)
  object varchar extends BaseCodec[String]:
    def apply()(using buf: ByteBuf) =
      buf.readUtf8(buf.readInt)
    def apply(a: String)(using buf: ByteBuf) =
      buf.writeInt(a.length)
      buf.writeUtf8(a)
  object char extends BaseCodec[Char]:
    def apply()(using buf: ByteBuf) =
      buf.ignoreInt
      buf.readByte.toChar
    def apply(a: Char)(using buf: ByteBuf) =
      buf.writeInt(1)
      buf.writeByte(a.toByte)
  object name extends BaseCodec[String]:
    def apply()(using buf: ByteBuf) =
      val len = buf.readInt
      assert(len < 64, s"name.maxlength (63) exceeded: $len")
      buf.readUtf8(len)
    def apply(a: String)(using buf: ByteBuf) =
      assert(a.length < 64, s"name.maxlength (63) exceeded: ${a.length}} $a")
      buf.writeInt(a.length)
      buf.writeUtf8(a)

  object _text extends BaseArrayCodec[String]:
    def apply()(using ByteBuf) = ArrayBuilder[String](text)
    def apply(a: Array[String])(using ByteBuf) = ArrayBuilder[String](a, text)
  object _varchar extends BaseArrayCodec[String]:
    def apply()(using ByteBuf) = ArrayBuilder[String](varchar)
    def apply(a: Array[String])(using ByteBuf) = ArrayBuilder[String](a, varchar)
  object _char extends BaseArrayCodec[Char]:
    def apply()(using ByteBuf) = ArrayBuilder[Char](char)
    def apply(a: Array[Char])(using ByteBuf) = ArrayBuilder[Char](a, char)
  object _name extends BaseArrayCodec[String]:
    def apply()(using ByteBuf) = ArrayBuilder[String](name)
    def apply(a: Array[String])(using ByteBuf) = ArrayBuilder[String](a, name)

  object bytea extends BaseCodec[Array[Byte]]:
    def apply()(using buf: ByteBuf) =
      buf.readByteArray(buf.readInt)
    def apply(a: Array[Byte])(using buf: ByteBuf) =
      buf.writeInt(a.length)
      buf.writeBytes(a)
  object _bytea extends BaseArrayCodec[Array[Byte]]:
    def apply()(using ByteBuf) = ArrayBuilder[Array[Byte]](bytea)
    def apply(a: Array[Array[Byte]])(using ByteBuf) = ArrayBuilder[Array[Byte]](a, bytea)

  inline private final val Epoch = 946684800L
  inline private final val AdjustMillis = 1000L
  inline private final val AdjustNanos = 1000000L
  inline private final val MillisPerDay = 8640000L
  private final val Utc = ZoneOffset.UTC

  object timestamptz extends BaseCodec[OffsetDateTime]:
    def apply()(using buf: ByteBuf) =
      buf.ignoreInt
      val timevalue = buf.readLong
      val seconds: Long = timevalue / AdjustNanos
      val nanos: Long = (timevalue - (seconds * AdjustNanos)) * AdjustMillis
      OffsetDateTime.ofInstant(Instant.ofEpochSecond(seconds + Epoch, nanos), Utc)
    def apply(a: OffsetDateTime)(using buf: ByteBuf) =
      val seconds: Long = (a.toEpochSecond - Epoch) * AdjustNanos
      val nanos: Long = a.getNano / AdjustMillis
      buf.writeInt(8)
      buf.writeLong(nanos + seconds)
  object timestamp extends BaseCodec[OffsetDateTime]:
    def apply()(using buf: ByteBuf) =
      buf.ignoreInt
      val timevalue = buf.readLong
      val seconds: Long = timevalue / AdjustNanos
      val nanos: Long = (timevalue - (seconds * AdjustNanos)) * AdjustMillis
      OffsetDateTime.ofInstant(Instant.ofEpochSecond(seconds + Epoch, nanos), Utc)
    def apply(a: OffsetDateTime)(using buf: ByteBuf) =
      val seconds: Long = (a.toEpochSecond - Epoch) * AdjustNanos
      val nanos: Long = a.getNano / AdjustMillis
      buf.writeInt(8)
      buf.writeLong(nanos + seconds)
  object _timestamptz extends BaseArrayCodec[OffsetDateTime]:
    def apply()(using ByteBuf) = ArrayBuilder[OffsetDateTime](timestamptz)
    def apply(a: Array[OffsetDateTime])(using ByteBuf) = ArrayBuilder[OffsetDateTime](a, timestamptz)
  object _timestamp extends BaseArrayCodec[OffsetDateTime]:
    def apply()(using ByteBuf) = ArrayBuilder[OffsetDateTime](timestamp)
    def apply(a: Array[OffsetDateTime])(using ByteBuf) = ArrayBuilder[OffsetDateTime](a, timestamp)

  object date extends BaseCodec[LocalDate]:
    def apply()(using buf: ByteBuf) =
      buf.ignoreInt
      val days = buf.readInt
      val millis: Long = (days * MillisPerDay) + Epoch
      LocalDate.ofInstant(Instant.ofEpochMilli(millis), Utc)
    def apply(a: LocalDate)(using buf: ByteBuf) =
      val epochmillis: Long = a.atStartOfDay.toInstant(Utc).toEpochMilli
      val days: Int = ((epochmillis - Epoch) / MillisPerDay).toInt
      buf.writeInt(4)
      buf.writeInt(days)
  object _date extends BaseArrayCodec[LocalDate]:
    def apply()(using ByteBuf) = ArrayBuilder[LocalDate](date)
    def apply(a: Array[LocalDate])(using ByteBuf) = ArrayBuilder[LocalDate](a, date)

  object interval extends BaseCodec[Interval]:
    def apply()(using buf: ByteBuf) =
      buf.ignoreInt
      val seclong = buf.readLong
      val days = buf.readInt
      val months = buf.readInt
      val sec: Double = seclong / 1000000d
      val hours: Int = (sec / (60 * 60)).toInt
      val minutes: Int = (sec / 60).toInt - (60 * hours)
      val seconds: Double = sec - (60 * minutes) - (60 * 60 * hours)
      val years: Int = months / 12
      Interval(years, months - (12 * years), days, hours, minutes, seconds)
    def apply(a: Interval)(using buf: ByteBuf) =
      val sec: Double = a.seconds + (60 * a.minutes) + (60 * 60 * a.hours)
      val seclong: Long = (1000000d * sec).toLong
      val months: Int = a.months + (12 * a.years)
      buf.writeInt(16)
      buf.writeLong(seclong)
      buf.writeInt(a.days)
      buf.writeInt(months)
  object _interval extends BaseArrayCodec[Interval]:
    def apply()(using ByteBuf) = ArrayBuilder[Interval](interval)
    def apply(a: Array[Interval])(using ByteBuf) = ArrayBuilder[Interval](a, interval)

  object bool extends BaseCodec[Boolean]:
    def apply()(using buf: ByteBuf) =
      buf.ignoreInt
      buf.readByte == 1
    def apply(a: Boolean)(using buf: ByteBuf) =
      val F = Array[Byte](0, 0, 0, 1, 0)
      val T = Array[Byte](0, 0, 0, 1, 1)
      buf.writeBytes(if a then T else F, 0, 5)
  object _bool extends BaseArrayCodec[Boolean]:
    def apply()(using ByteBuf) = ArrayBuilder[Boolean](bool)
    def apply(a: Array[Boolean])(using ByteBuf) = ArrayBuilder[Boolean](a, bool)

  object uuid extends BaseCodec[Uuid]:
    def apply()(using buf: ByteBuf) =
      buf.ignoreInt
      Uuid.fromBytes(buf.readByteArray(16))
    def apply(a: Uuid)(using buf: ByteBuf) =
      buf.writeInt(16)
      buf.writeBytes(a.toBytes, 0, 16)
  object _uuid extends BaseArrayCodec[Uuid]:
    def apply()(using ByteBuf) = ArrayBuilder[Uuid](uuid)
    def apply(a: Array[Uuid])(using ByteBuf) = ArrayBuilder[Uuid](a, uuid)

  final lazy val Types: Map[BaseEncoder[?] | BaseArrayEncoder[?], Int] = Map(
    bool -> 16,
    bytea -> 17,
    char -> 18,
    name -> 19,
    int8 -> 20,
    int2 -> 21,
    int4 -> 23,
    text -> 25,
    float4 -> 700,
    float8 -> 701,
    varchar -> 1043,
    date -> 1082,
    timestamp -> 1114,
    timestamptz -> 1184,
    interval -> 1186,
    numeric -> 1700,
    uuid -> 2950,
    _bool -> 1000,
    _bytea -> 1001,
    _char -> 1002,
    _name -> 1003,
    _int2 -> 1005,
    _int4 -> 1007,
    _text -> 1009,
    _varchar -> 1015,
    _int8 -> 1016,
    _float4 -> 1021,
    _float8 -> 1022,
    _timestamp -> 1115,
    _date -> 1182,
    _timestamptz -> 1185,
    _interval -> 1187,
    _numeric -> 1231,
    _uuid -> 2951
  )

  private def fromOid(oid: Int): BaseEncoder[?] | BaseArrayEncoder[?] = Types.find(_._2 == oid).map(_._1).get

  private[pgcopy] def nameForOid(oid: Int): String = fromOid(oid).getClass.getSimpleName match
    case n if n.endsWith("$") => n.nn.dropRight(1)
    case n                    => n

  extension (buf: ByteBuf)
    inline def ignore(len: Int): Unit =
      buf.readerIndex(buf.readerIndex + len)
    inline def ignoreInt: Unit =
      buf.readerIndex(buf.readerIndex + 4)
    inline def readUtf8(len: Int): String =
      String.valueOf(buf.readCharSequence(len, UTF_8))
    inline def readUtf8z: String =
      var i = buf.readerIndex
      while buf.getByte(i) != 0 do i += 1
      val res = String.valueOf(buf.readCharSequence(i - buf.readerIndex, UTF_8))
      buf.readByte
      res
    inline def readByteArray(len: Int): Array[Byte] =
      val arr = Array.ofDim[Byte](len)
      buf.readBytes(arr)
      arr
    inline def readRemaining: Array[Byte] =
      buf.readByteArray(buf.readableBytes)
    inline def testUtf8(s: String): Boolean =
      if buf.readableBytes < s.length then false
      else s == String.valueOf(buf.getCharSequence(buf.readerIndex, s.length, UTF_8))
    inline def writeUtf8(s: String): ByteBuf =
      buf.writeBytes(s.getBytes(UTF_8))
    inline def writeUtf8z(s: String): ByteBuf =
      writeUtf8(s)
      buf.writeByte(0)

inline private given nn_conversion[A]: Conversion[A | Null, A] = _.nn
