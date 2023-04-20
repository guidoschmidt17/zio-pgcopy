package zio
package pgcopy

import io.netty.buffer.ByteBuf

import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import scala.deriving.Mirror.ProductOf
import scala.reflect.ClassTag

trait Decoder[A]:
  def apply()(using ByteBuf): A

trait Encoder[A]:
  def apply(a: A)(using ByteBuf): Unit

trait Codec[A] extends Encoder[A], Decoder[A]

object Codec:

  final class BiCodec[A](decode: ByteBuf ?=> A, encode: A => ByteBuf ?=> Unit) extends Codec[A]:
    inline final def apply()(using ByteBuf): A = decode
    inline final def apply(a: A)(using ByteBuf): Unit = encode(a)

  object Decoder:
    def apply[A <: Product]()(using buf: ByteBuf)(using m: ProductOf[A], d: Decoder[m.MirroredElemTypes]): A =
      m.fromProduct(d())
  inline given Decoder[EmptyTuple] with
    def apply()(using ByteBuf): EmptyTuple = EmptyTuple
  inline given [H: Decoder, T <: Tuple: Decoder]: Decoder[H *: T] with
    def apply()(using ByteBuf): H *: T =
      summon[Decoder[H]]() *: summon[Decoder[T]]()

  object Encoder:
    def apply[A <: Product](a: A)(using buf: ByteBuf)(using m: ProductOf[A], e: Encoder[m.MirroredElemTypes]): Unit =
      buf.writeShort(a.productArity)
      e(Tuple.fromProductTyped(a))
  inline given Encoder[EmptyTuple] with
    def apply(e: EmptyTuple)(using ByteBuf): Unit = ()
  inline given [H: Encoder, T <: Tuple: Encoder]: Encoder[H *: T] with
    def apply(tuple: H *: T)(using ByteBuf): Unit =
      summon[Encoder[H]](tuple.head)
      summon[Encoder[T]](tuple.tail)

  protected sealed trait BaseDecoder[A] extends Decoder[A]
  protected sealed trait BaseEncoder[A] extends Encoder[A]:
    final lazy val typeoid: Int = Types.get(this).getOrElse(text.typeoid)
  protected sealed trait BaseCodec[A] extends BaseEncoder[A], BaseDecoder[A]
  protected sealed trait BaseArrayCodec[A] extends BaseEncoder[Array[A]], BaseDecoder[Array[A]]

  import Util.*

  private final class ArrayBuilder[A: ClassTag]:
    def apply(decoder: BaseDecoder[A])(using buf: ByteBuf): Array[A] =
      buf.ignoreArrayHeader
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
    def apply[A: ClassTag](decoder: BaseDecoder[A])(using ByteBuf): Array[A] = new ArrayBuilder[A].apply(decoder)
    def apply[A: ClassTag](a: Array[A], encoder: BaseEncoder[A])(using ByteBuf): Unit = new ArrayBuilder[A].apply(a, encoder)

  object int2 extends BaseCodec[Short]:
    def apply()(using buf: ByteBuf) =
      buf.ignoreInt
      buf.readShort
    def apply(a: Short)(using buf: ByteBuf) =
      buf.writeInt(2)
      buf.writeShort(a)
  given BaseCodec[Short] = int2
  object int4 extends BaseCodec[Int]:
    def apply()(using buf: ByteBuf) =
      buf.ignoreInt
      buf.readInt
    def apply(a: Int)(using buf: ByteBuf) =
      buf.writeInt(4)
      buf.writeInt(a)
  given BaseCodec[Int] = int4
  object int8 extends BaseCodec[Long]:
    def apply()(using buf: ByteBuf) =
      buf.ignoreInt
      buf.readLong
    def apply(a: Long)(using buf: ByteBuf) =
      buf.writeInt(8)
      buf.writeLong(a)
  given BaseCodec[Long] = int8

  object float4 extends BaseCodec[Float]:
    def apply()(using buf: ByteBuf) =
      buf.ignoreInt
      buf.readFloat
    def apply(a: Float)(using buf: ByteBuf) =
      buf.writeInt(4)
      buf.writeFloat(a)
  given BaseCodec[Float] = float4
  object float8 extends BaseCodec[Double]:
    def apply()(using buf: ByteBuf) =
      buf.ignoreInt
      buf.readDouble
    def apply(a: Double)(using buf: ByteBuf) =
      buf.writeInt(8)
      buf.writeDouble(a)
  given BaseCodec[Double] = float8
  object numeric extends BaseCodec[BigDecimal]:
    def apply()(using buf: ByteBuf) =
      buf.ignoreInt
      val len = buf.readShort & 0xffff
      val weight = buf.readShort
      val sign = buf.readShort
      val scale = buf.readShort
      val digits = Range(0, len).map(_ => buf.readShort)
      NumericComponents(weight, sign, scale, digits)
    def apply(a: BigDecimal)(using buf: ByteBuf) =
      val num = NumericComponents(a)
      buf.writeInt(num.bufferlen)
      buf.writeShort(num.len)
      buf.writeShort(num.w)
      buf.writeShort(num.sign)
      buf.writeShort(num.scale)
      num.digits.foreach(d => buf.writeShort(d))
  given BaseCodec[BigDecimal] = numeric

  object _int2 extends BaseArrayCodec[Short]:
    def apply()(using ByteBuf) = ArrayBuilder[Short](int2)
    def apply(a: Array[Short])(using ByteBuf) = ArrayBuilder[Short](a, int2)
  given BaseArrayCodec[Short] = _int2
  object _int4 extends BaseArrayCodec[Int]:
    def apply()(using ByteBuf) = ArrayBuilder[Int](int4)
    def apply(a: Array[Int])(using ByteBuf) = ArrayBuilder[Int](a, int4)
  given BaseArrayCodec[Int] = _int4
  object _int8 extends BaseArrayCodec[Long]:
    def apply()(using ByteBuf) = ArrayBuilder[Long](int8)
    def apply(a: Array[Long])(using ByteBuf) = ArrayBuilder[Long](a, int8)
  given BaseArrayCodec[Long] = _int8
  object _float4 extends BaseArrayCodec[Float]:
    def apply()(using ByteBuf) = ArrayBuilder[Float](float4)
    def apply(a: Array[Float])(using ByteBuf) = ArrayBuilder[Float](a, float4)
  given BaseArrayCodec[Float] = _float4
  object _float8 extends BaseArrayCodec[Double]:
    def apply()(using ByteBuf) = ArrayBuilder[Double](float8)
    def apply(a: Array[Double])(using ByteBuf) = ArrayBuilder[Double](a, float8)
  given BaseArrayCodec[Double] = _float8
  object _numeric extends BaseArrayCodec[BigDecimal]:
    def apply()(using ByteBuf) = ArrayBuilder[BigDecimal](numeric)
    def apply(a: Array[BigDecimal])(using ByteBuf) = ArrayBuilder[BigDecimal](a, numeric)
  given BaseArrayCodec[BigDecimal] = _numeric

  object text extends BaseCodec[String]:
    def apply()(using buf: ByteBuf) =
      buf.readUtf8(buf.readInt)
    def apply(a: String)(using buf: ByteBuf) =
      buf.writeUtf8(a)
    def apply(a: Any)(using buf: ByteBuf) =
      buf.writeUtf8(a.toString)
  given BaseCodec[String] = text
  object varchar extends BaseCodec[String]:
    def apply()(using buf: ByteBuf) =
      buf.readUtf8(buf.readInt)
    def apply(a: String)(using buf: ByteBuf) =
      buf.writeUtf8(a)
  object name extends BaseCodec[String]:
    def apply()(using buf: ByteBuf) =
      val len = buf.readInt
      buf.readUtf8(math.min(len, 63))
    def apply(a: String)(using buf: ByteBuf) =
      buf.writeUtf8(a.take(63))
  object char extends BaseCodec[Char]:
    def apply()(using buf: ByteBuf) =
      buf.ignoreInt
      buf.readByte.toChar
    def apply(a: Char)(using buf: ByteBuf) =
      buf.writeInt(1)
      buf.writeByte(a.toByte)
  given BaseCodec[Char] = char
  object json extends BaseCodec[String]:
    def apply()(using buf: ByteBuf) =
      buf.readUtf8(buf.readInt)
    def apply(a: String)(using buf: ByteBuf) =
      buf.writeUtf8(a)
    def apply(a: Any)(using buf: ByteBuf) =
      buf.writeUtf8(a.toString)
  object jsonb extends BaseCodec[String]:
    def apply()(using buf: ByteBuf) =
      val len = buf.readInt
      buf.readByte
      buf.readUtf8(len - 1)
    def apply(a: String)(using buf: ByteBuf) =
      buf.writeInt(1 + a.length)
      buf.writeByte(1)
      buf.writeUtf8s(a)
    def apply(a: Any)(using buf: ByteBuf) =
      val s = a.toString
      buf.writeInt(1 + s.length)
      buf.writeByte(1)
      buf.writeUtf8s(s)

  object _text extends BaseArrayCodec[String]:
    def apply()(using ByteBuf) = ArrayBuilder[String](text)
    def apply(a: Array[String])(using ByteBuf) = ArrayBuilder[String](a, text)
  given BaseArrayCodec[String] = _text
  object _varchar extends BaseArrayCodec[String]:
    def apply()(using ByteBuf) = ArrayBuilder[String](varchar)
    def apply(a: Array[String])(using ByteBuf) = ArrayBuilder[String](a, varchar)
  object _json extends BaseArrayCodec[String]:
    def apply()(using ByteBuf) = ArrayBuilder[String](json)
    def apply(a: Array[String])(using ByteBuf) = ArrayBuilder[String](a, json)
  object _char extends BaseArrayCodec[Char]:
    def apply()(using ByteBuf) = ArrayBuilder[Char](char)
    def apply(a: Array[Char])(using ByteBuf) = ArrayBuilder[Char](a, char)
  given BaseArrayCodec[Char] = _char
  object _name extends BaseArrayCodec[String]:
    def apply()(using ByteBuf) = ArrayBuilder[String](name)
    def apply(a: Array[String])(using ByteBuf) = ArrayBuilder[String](a, name)
  object _jsonb extends BaseArrayCodec[String]:
    def apply()(using ByteBuf) = ArrayBuilder[String](jsonb)
    def apply(a: Array[String])(using ByteBuf) = ArrayBuilder[String](a, jsonb)

  object bytea extends BaseCodec[Array[Byte]]:
    def apply()(using buf: ByteBuf) =
      buf.readByteArray(buf.readInt)
    def apply(a: Array[Byte])(using buf: ByteBuf) =
      val len = a.length
      buf.writeInt(len)
      buf.writeBytes(a, 0, len)
  given BaseCodec[Array[Byte]] = bytea
  object _bytea extends BaseArrayCodec[Array[Byte]]:
    def apply()(using ByteBuf) = ArrayBuilder[Array[Byte]](bytea)
    def apply(a: Array[Array[Byte]])(using ByteBuf) = ArrayBuilder[Array[Byte]](a, bytea)
  given BaseArrayCodec[Array[Byte]] = _bytea

  inline private final val Epoch = 946684800L
  inline private final val AdjustMillis = 1000L
  inline private final val AdjustNanos = 1000000L
  inline private final val MillisPerDay = 8640000L
  inline private final def Utc = ZoneOffset.UTC

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
  given BaseCodec[OffsetDateTime] = timestamptz
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
  given BaseArrayCodec[OffsetDateTime] = _timestamptz
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
  given BaseCodec[LocalDate] = date
  object _date extends BaseArrayCodec[LocalDate]:
    def apply()(using ByteBuf) = ArrayBuilder[LocalDate](date)
    def apply(a: Array[LocalDate])(using ByteBuf) = ArrayBuilder[LocalDate](a, date)
  given BaseArrayCodec[LocalDate] = _date

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
  given BaseCodec[Interval] = interval
  object _interval extends BaseArrayCodec[Interval]:
    def apply()(using ByteBuf) = ArrayBuilder[Interval](interval)
    def apply(a: Array[Interval])(using ByteBuf) = ArrayBuilder[Interval](a, interval)
  given BaseArrayCodec[Interval] = _interval

  object bool extends BaseCodec[Boolean]:
    def apply()(using buf: ByteBuf) =
      buf.ignoreInt
      buf.readByte == 1
    def apply(a: Boolean)(using buf: ByteBuf) =
      val T = Array[Byte](0, 0, 0, 1, 1)
      val F = Array[Byte](0, 0, 0, 1, 0)
      buf.writeBytes(if a then T else F, 0, 5)
  given BaseCodec[Boolean] = bool
  object _bool extends BaseArrayCodec[Boolean]:
    def apply()(using ByteBuf) = ArrayBuilder[Boolean](bool)
    def apply(a: Array[Boolean])(using ByteBuf) = ArrayBuilder[Boolean](a, bool)
  given BaseArrayCodec[Boolean] = _bool

  object uuid extends BaseCodec[Uuid]:
    def apply()(using buf: ByteBuf) =
      buf.ignoreInt
      Uuid.read(buf)
    def apply(a: Uuid)(using buf: ByteBuf) =
      buf.writeInt(16)
      a.write(buf)
  given BaseCodec[Uuid] = uuid
  object _uuid extends BaseArrayCodec[Uuid]:
    def apply()(using ByteBuf) = ArrayBuilder[Uuid](uuid)
    def apply(a: Array[Uuid])(using ByteBuf) = ArrayBuilder[Uuid](a, uuid)
  given BaseArrayCodec[Uuid] = _uuid

  final private lazy val Types: Map[BaseEncoder[?], Int] = Map(
    bool -> 16,
    bytea -> 17,
    char -> 18,
    name -> 19,
    int8 -> 20,
    int2 -> 21,
    int4 -> 23,
    text -> 25,
    json -> 114,
    float4 -> 700,
    float8 -> 701,
    varchar -> 1043,
    date -> 1082,
    timestamp -> 1114,
    timestamptz -> 1184,
    interval -> 1186,
    numeric -> 1700,
    uuid -> 2950,
    jsonb -> 3802,
    _json -> 199,
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
    _uuid -> 2951,
    _jsonb -> 3807
  )

  private def fromOid(oid: Int): BaseEncoder[?] =
    Types.find(_._2 == oid).map(_._1).getOrElse(text)

  private[pgcopy] def nameForOid(oid: Int): String = fromOid(oid).getClass.getSimpleName match
    case n if n.endsWith("$") => n.nn.dropRight(1)
    case n                    => n

  extension (buf: ByteBuf)
    inline private[pgcopy] def ignoreInt: Unit =
      buf.readerIndex(buf.readerIndex + 4)
    inline private[pgcopy] def ignoreArrayHeader: Unit =
      buf.readerIndex(buf.readerIndex + 16)
    inline private[pgcopy] def ignoreCopyOutHeader: Unit =
      buf.readerIndex(buf.readerIndex + 19)
    inline private[pgcopy] def readUtf8(len: Int): String =
      String.valueOf(buf.readCharSequence(len, UTF_8))
    inline private[pgcopy] def readUtf8z: String =
      var i = buf.readerIndex
      while buf.getByte(i) != 0 do i += 1
      val res = String.valueOf(buf.readCharSequence(i - buf.readerIndex, UTF_8))
      buf.readerIndex(buf.readerIndex + 1)
      res
    inline private[pgcopy] def readByteArray(len: Int): Array[Byte] =
      val arr = Array.ofDim[Byte](len)
      buf.readBytes(arr, 0, len)
      arr
    inline private[pgcopy] def readRemaining: Array[Byte] =
      buf.readByteArray(buf.readableBytes)
    inline private[pgcopy] def writeUtf8z(s: String): ByteBuf =
      buf.writeBytes(s.getBytes(UTF_8))
      buf.writeByte(0)
    inline private[pgcopy] def writeUtf8s(s: String): ByteBuf =
      buf.writeBytes(s.getBytes(UTF_8))
    inline private[pgcopy] def writeUtf8(s: String): ByteBuf =
      val bytes = s.getBytes(UTF_8)
      val len = bytes.length
      buf.writeInt(len)
      buf.writeBytes(bytes, 0, len)

inline private given [A]: Conversion[A | Null, A] = _.nn
