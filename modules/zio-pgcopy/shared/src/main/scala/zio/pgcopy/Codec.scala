package zio
package pgcopy

import io.netty.buffer.ByteBuf

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import scala.annotation.switch
import scala.reflect.ClassTag

trait Decoder[A]:
  def apply()(using ByteBuf): A
  val typename = s"${this.getClass.getSimpleName.nn.takeWhile(_ != '$')}"
trait SeqDecoder[A] extends Decoder[Seq[A]]:
  def apply()(using ByteBuf): Seq[A]

trait Encoder[A]:
  def apply(a: A)(using ByteBuf): Unit
trait SeqEncoder[A] extends Encoder[Seq[A]]:
  def apply(aseq: Seq[A])(using ByteBuf): Unit

trait Codec[A] extends Encoder[A], Decoder[A]
trait SeqCodec[A] extends SeqEncoder[A], SeqDecoder[A]

object Codec:

  sealed trait BaseEncoder[A] extends Encoder[A]:
    final lazy val typeoid: Int = Types.get(this).get
  sealed trait BaseSeqEncoder[A] extends SeqEncoder[A]:
    final lazy val typeoid: Int = Types.get(this).get
  sealed trait BaseCodec[A] extends Codec[A], BaseEncoder[A]
  sealed trait BaseSeqCodec[A] extends SeqCodec[A], BaseSeqEncoder[A]

  private class SeqBuilder[A: ClassTag]:
    def apply(decoder: Decoder[A])(using buf: ByteBuf): Seq[A] =
      val blen = buf.readInt
      val dimensions = buf.readInt
      val nullallowed = buf.readInt
      val typeoid = buf.readInt
      val len = buf.readInt
      val usedef = buf.readInt
      val arr = Array.ofDim[A](len)
      Range(0, len).foreach(i => arr.update(i, decoder()))
      arr.toSeq
    def apply(aseq: Seq[A], encoder: BaseEncoder[A])(using buf: ByteBuf): Unit =
      val i = buf.writerIndex
      val dimensions = 1
      val nullallowed = 0
      val typeoid = encoder.typeoid
      val len = aseq.length
      val usedef = 0
      buf.writeInt(-1)
      buf.writeInt(dimensions)
      buf.writeInt(nullallowed)
      buf.writeInt(typeoid)
      buf.writeInt(len)
      buf.writeInt(usedef)
      aseq.foreach(a => encoder(a))
      val blen = buf.writerIndex - i - 4
      buf.setInt(i, blen)

  private object SeqBuilder:
    def apply[A: ClassTag](decoder: Decoder[A])(using ByteBuf): Seq[A] = new SeqBuilder[A].apply(decoder)
    def apply[A: ClassTag](aseq: Seq[A], encoder: BaseEncoder[A])(using ByteBuf): Unit = new SeqBuilder[A].apply(aseq, encoder)

  object fields extends Codec[Short]:
    def apply()(using buf: ByteBuf) =
      buf.readShort
    def apply(a: Short)(using buf: ByteBuf) =
      buf.writeShort(a)

  object int2 extends BaseCodec[Short]:
    def apply()(using buf: ByteBuf) =
      buf.readInt
      buf.readShort
    def apply(a: Short)(using buf: ByteBuf) =
      buf.writeInt(2)
      buf.writeShort(a)
  object int4 extends BaseCodec[Int]:
    def apply()(using buf: ByteBuf) =
      buf.readInt
      buf.readInt
    def apply(a: Int)(using buf: ByteBuf) =
      buf.writeInt(4)
      buf.writeInt(a)
  object int8 extends BaseCodec[Long]:
    def apply()(using buf: ByteBuf) =
      buf.readInt
      buf.readLong
    def apply(a: Long)(using buf: ByteBuf) =
      buf.writeInt(8)
      buf.writeLong(a)
  object float4 extends BaseCodec[Float]:
    def apply()(using buf: ByteBuf) =
      buf.readInt
      buf.readFloat
    def apply(a: Float)(using buf: ByteBuf) =
      buf.writeInt(4)
      buf.writeFloat(a)
  object float8 extends BaseCodec[Double]:
    def apply()(using buf: ByteBuf) =
      buf.readInt
      buf.readDouble
    def apply(a: Double)(using buf: ByteBuf) =
      buf.writeInt(8)
      buf.writeDouble(a)
  object numeric extends BaseCodec[BigDecimal]:
    def apply()(using ByteBuf) = BigDecimal(text())
    def apply(a: BigDecimal)(using buf: ByteBuf) =
      buf.writeUtf8z(s"'$a'::numeric")

  object _int2 extends BaseSeqCodec[Short]:
    def apply()(using ByteBuf) = SeqBuilder[Short](int2)
    def apply(a: Seq[Short])(using ByteBuf) = SeqBuilder[Short](a, int2)
  object _int4 extends BaseSeqCodec[Int]:
    def apply()(using ByteBuf) = SeqBuilder[Int](int4)
    def apply(a: Seq[Int])(using ByteBuf) = SeqBuilder[Int](a, int4)
  object _int8 extends BaseSeqCodec[Long]:
    def apply()(using ByteBuf) = SeqBuilder[Long](int8)
    def apply(a: Seq[Long])(using ByteBuf) = SeqBuilder[Long](a, int8)
  object _float4 extends BaseSeqCodec[Float]:
    def apply()(using ByteBuf) = SeqBuilder[Float](float4)
    def apply(a: Seq[Float])(using ByteBuf) = SeqBuilder[Float](a, float4)
  object _float8 extends BaseSeqCodec[Double]:
    def apply()(using ByteBuf) = SeqBuilder[Double](float8)
    def apply(a: Seq[Double])(using ByteBuf) = SeqBuilder[Double](a, float8)
  object _numeric extends BaseSeqCodec[BigDecimal]:
    def apply()(using ByteBuf) = SeqBuilder[BigDecimal](numeric)
    def apply(a: Seq[BigDecimal])(using ByteBuf) = SeqBuilder[BigDecimal](a, numeric)

  object text extends BaseCodec[String]:
    def apply()(using buf: ByteBuf) = buf.readUtf8(buf.readInt)
    def apply(a: String)(using buf: ByteBuf) =
      buf.writeInt(a.length)
      buf.writeUtf8(a)
  object varchar extends BaseCodec[String]:
    def apply()(using buf: ByteBuf) = buf.readUtf8(buf.readInt)
    def apply(a: String)(using buf: ByteBuf) =
      buf.writeInt(a.length)
      buf.writeUtf8(a)
  object char extends BaseCodec[Char]:
    def apply()(using buf: ByteBuf) =
      buf.readInt
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

  object _text extends BaseSeqCodec[String]:
    def apply()(using ByteBuf) = SeqBuilder[String](text)
    def apply(a: Seq[String])(using ByteBuf) = SeqBuilder[String](a, text)
  object _varchar extends BaseSeqCodec[String]:
    def apply()(using ByteBuf) = SeqBuilder[String](varchar)
    def apply(a: Seq[String])(using ByteBuf) = SeqBuilder[String](a, varchar)
  object _char extends BaseSeqCodec[Char]:
    def apply()(using ByteBuf) = SeqBuilder[Char](char)
    def apply(a: Seq[Char])(using ByteBuf) = SeqBuilder[Char](a, char)
  object _name extends BaseSeqCodec[String]:
    def apply()(using ByteBuf) = SeqBuilder[String](name)
    def apply(a: Seq[String])(using ByteBuf) = SeqBuilder[String](a, name)

  object bytea extends BaseCodec[Array[Byte]]:
    def apply()(using buf: ByteBuf) = buf.readByteArray(buf.readInt)
    def apply(a: Array[Byte])(using buf: ByteBuf) =
      buf.writeInt(a.length)
      buf.writeBytes(a)
  object _bytea extends BaseSeqCodec[Array[Byte]]:
    def apply()(using ByteBuf) = SeqBuilder[Array[Byte]](bytea)
    def apply(a: Seq[Array[Byte]])(using ByteBuf) = SeqBuilder[Array[Byte]](a, bytea)

  object timestamptz extends BaseCodec[OffsetDateTime]:
    inline private final val Epoch = 946684800L
    inline private final val AdjustMillis = 1000L
    inline private final val AdjustNanos = 1000000L
    private final val Utc = ZoneOffset.UTC
    def apply()(using buf: ByteBuf) =
      buf.readInt
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
    inline private final val Epoch = 946684800L
    inline private final val AdjustMillis = 1000L
    inline private final val AdjustNanos = 1000000L
    private final val Utc = ZoneOffset.UTC
    def apply()(using buf: ByteBuf) =
      buf.readInt
      val timevalue = buf.readLong
      val seconds: Long = timevalue / AdjustNanos
      val nanos: Long = (timevalue - (seconds * AdjustNanos)) * AdjustMillis
      OffsetDateTime.ofInstant(Instant.ofEpochSecond(seconds + Epoch, nanos), Utc)
    def apply(a: OffsetDateTime)(using buf: ByteBuf) =
      val seconds: Long = (a.toEpochSecond - Epoch) * AdjustNanos
      val nanos: Long = a.getNano / AdjustMillis
      buf.writeInt(8)
      buf.writeLong(nanos + seconds)
  object _timestamptz extends BaseSeqCodec[OffsetDateTime]:
    def apply()(using ByteBuf) = SeqBuilder[OffsetDateTime](timestamptz)
    def apply(a: Seq[OffsetDateTime])(using ByteBuf) = SeqBuilder[OffsetDateTime](a, timestamptz)
  object _timestamp extends BaseSeqCodec[OffsetDateTime]:
    def apply()(using ByteBuf) = SeqBuilder[OffsetDateTime](timestamp)
    def apply(a: Seq[OffsetDateTime])(using ByteBuf) = SeqBuilder[OffsetDateTime](a, timestamp)

  object date extends BaseCodec[LocalDate]:
    inline private final val Epoch = 946684800L
    inline private final val MillisPerDay = 8640000L
    private final val Utc = ZoneOffset.UTC
    def apply()(using buf: ByteBuf) =
      buf.readInt
      val days = buf.readInt
      val millis: Long = (days * MillisPerDay) + Epoch
      LocalDate.ofInstant(Instant.ofEpochMilli(millis), Utc)
    def apply(a: LocalDate)(using buf: ByteBuf) =
      val epochmillis: Long = a.atStartOfDay.toInstant(Utc).toEpochMilli
      val days: Int = ((epochmillis - Epoch) / MillisPerDay).toInt
      buf.writeInt(4)
      buf.writeInt(days)
  object _date extends BaseSeqCodec[LocalDate]:
    def apply()(using ByteBuf) = SeqBuilder[LocalDate](date)
    def apply(a: Seq[LocalDate])(using ByteBuf) = SeqBuilder[LocalDate](a, date)

  case class IntervalHolder(years: Int, months: Int, days: Int, hours: Int, minutes: Int, seconds: Double)

  object interval extends BaseCodec[IntervalHolder]:
    def apply()(using buf: ByteBuf) =
      buf.readInt
      val seclong = buf.readLong
      val days = buf.readInt
      val months = buf.readInt
      val sec: Double = seclong / 1000000d
      val hours: Int = (sec / (60 * 60)).toInt
      val minutes: Int = (sec / 60).toInt - (60 * hours)
      val seconds: Double = sec - (60 * minutes) - (60 * 60 * hours)
      val years: Int = months / 12
      IntervalHolder(years, months - (12 * years), days, hours, minutes, seconds)
    def apply(a: IntervalHolder)(using buf: ByteBuf) =
      val sec: Double = a.seconds + (60 * a.minutes) + (60 * 60 * a.hours)
      val seclong: Long = (1000000d * sec).toLong
      val months: Int = a.months + (12 * a.years)
      buf.writeInt(16)
      buf.writeLong(seclong)
      buf.writeInt(a.days)
      buf.writeInt(months)
  object _interval extends BaseSeqCodec[IntervalHolder]:
    def apply()(using ByteBuf) = SeqBuilder[IntervalHolder](interval)
    def apply(a: Seq[IntervalHolder])(using ByteBuf) = SeqBuilder[IntervalHolder](a, interval)

  object bool extends BaseCodec[Boolean]:
    def apply()(using buf: ByteBuf) =
      buf.readInt
      buf.readByte == 1
    def apply(a: Boolean)(using buf: ByteBuf) =
      buf.writeInt(1)
      buf.writeByte(if a then 1 else 0)
  object _bool extends BaseSeqCodec[Boolean]:
    def apply()(using ByteBuf) = SeqBuilder[Boolean](bool)
    def apply(a: Seq[Boolean])(using ByteBuf) = SeqBuilder[Boolean](a, bool)

  object uuid extends BaseCodec[Uuid]:
    def apply()(using buf: ByteBuf) = Uuid.fromBytes(buf.readByteArray(buf.readInt))
    def apply(a: Uuid)(using buf: ByteBuf) =
      buf.writeInt(16)
      buf.writeBytes(a.toBytes)
  object _uuid extends BaseSeqCodec[Uuid]:
    def apply()(using ByteBuf) = SeqBuilder[Uuid](uuid)
    def apply(a: Seq[Uuid])(using ByteBuf) = SeqBuilder[Uuid](a, uuid)

  final lazy val Types: Map[BaseEncoder[?] | BaseSeqEncoder[?], Int] = Map(
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

  def fromOid(oid: Int): BaseEncoder[?] | BaseSeqEncoder[?] = Types.find(_._2 == oid).map(_._1).get

  def nameForOid(oid: Int): String = fromOid(oid).getClass.getSimpleName match
    case n if n.endsWith("$") => n.nn.dropRight(1)
    case n                    => n
