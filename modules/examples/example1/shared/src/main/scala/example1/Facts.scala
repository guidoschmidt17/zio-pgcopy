package example1

import io.netty.buffer.ByteBuf
import zio.Random.*
import zio.*
import zio.pgcopy.Codec.*
import zio.pgcopy.Codec.given
import zio.pgcopy.Util.Uuid
import zio.pgcopy.*

object Event:
  enum Category:
    case Created, Read, Updated, Deleted, Meta
  given Codec[Event.Category] = BiCodec(Category.valueOf(text()), text(_))

case class Fact(
    aggregateid: Uuid,
    aggregatelatest: Int,
    eventcategory: Event.Category,
    eventid: Uuid,
    eventdatalength: Int,
    eventdata: Array[Byte],
    tags: Array[String]
)

given Codec[Fact] = BiCodec[Fact](Decoder(), Encoder(_))

object Fact:
  def randomFact(aggregateid: Uuid, aggregatelatest: Int): UIO[Fact] =
    for
      ec <- nextIntBounded(4)
      eventid <- Uuid.nextUuid
      eventdatalength <- nextIntBetween(5, 100)
      eventdata <- nextBytes(eventdatalength)
      tags = Array("bla", "blabla")
    yield Fact(
      aggregateid,
      aggregatelatest,
      Event.Category.fromOrdinal(ec),
      eventid,
      eventdatalength,
      eventdata.toArray,
      tags
    )
  def randomFacts(n: Int): UIO[Chunk[Fact]] =
    for
      aggregateid <- nextUUID
      facts <- ZIO.foreach(Range(0, n))(aggregatelatest => randomFact(aggregateid, aggregatelatest))
    yield Chunk.fromIterable(facts)
