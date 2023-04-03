package zio
package pgcopy
package util

import zio.*

import java.nio.ByteBuffer
import java.util.UUID

case class Interval(years: Int, months: Int, days: Int, hours: Int, minutes: Int, seconds: Double)

case class Uuid private (uuid: UUID):

  override def toString = uuid.toString

  final val toBytes: Array[Byte] =
    val bb = ByteBuffer.wrap(Array.ofDim[Byte](16))
    bb.putLong(uuid.getMostSignificantBits)
    bb.putLong(uuid.getLeastSignificantBits)
    bb.array

object Uuid:

  def apply(uuid: UUID): Uuid = new Uuid(uuid)

  inline def apply(s: String): Uuid = fromString(s)

  inline def fromString(s: String): Uuid = Uuid(UUID.fromString(s))

  inline def fromBytes(bytes: Array[Byte]): Uuid =
    val bb = ByteBuffer.wrap(bytes, 0, 16)
    val most = bb.getLong
    val least = bb.getLong
    Uuid(new UUID(most, least))

  inline def parseString(s: String): IO[IllegalArgumentException, Uuid] = ZIO.succeed(Uuid(UUID.fromString(s)))

  inline def randomUuid: Uuid = Uuid(UUID.randomUUID)

  inline def nextUuid: UIO[Uuid] = Random.nextUUID.flatMap(uuid => ZIO.succeed(Uuid(uuid)))

  final val empty = fromString("00000000-0000-0000-0000-000000000000".intern)

  inline given Conversion[Uuid, UUID] = _.uuid

  inline given Conversion[UUID, Uuid] = Uuid(_)
