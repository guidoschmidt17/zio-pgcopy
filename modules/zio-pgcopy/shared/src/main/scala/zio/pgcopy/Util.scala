package zio
package pgcopy

import io.netty.buffer.ByteBuf
import zio.*

import java.util.UUID

object Util:

  case class Interval(years: Int, months: Int, days: Int, hours: Int, minutes: Int, seconds: Double)

  class Uuid(val uuid: UUID) extends AnyVal:

    override def toString = uuid.toString

    inline def write(buf: ByteBuf) =
      buf.writeLong(uuid.getMostSignificantBits)
      buf.writeLong(uuid.getLeastSignificantBits)

  object Uuid:

    inline def read(buf: ByteBuf) = Uuid(new UUID(buf.readLong, buf.readLong))

    inline def nextUuid: UIO[Uuid] = Random.nextUUID.flatMap(uuid => ZIO.succeed(Uuid(uuid)))

    inline given Conversion[Uuid, UUID] = _.uuid

    inline given Conversion[UUID, Uuid] = Uuid(_)

  inline final def ceilPower2(i: Int): Int =
    var x = i - 1
    x |= x >> 1
    x |= x >> 2
    x |= x >> 4
    x |= x >> 8
    x |= x >> 16
    x + 1
