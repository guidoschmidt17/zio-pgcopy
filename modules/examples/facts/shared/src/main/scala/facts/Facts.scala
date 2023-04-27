package facts

import zio.*
import zio.pgcopy.*
import zio.pgcopy.given

import Util.Uuid

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

object Narrow:
  case class Fact(
      aggregatelatest: Int,
      eventcategory: Event.Category,
      eventid: Uuid,
      eventdatalength: Int,
      eventdata: Array[Byte],
      tags: Array[String]
  )

given Codec[Fact] = BiCodec[Fact](Decoder(), Encoder(_))
given narrow: Codec[Narrow.Fact] = BiCodec[Narrow.Fact](Decoder(), Encoder(_))

object Fact:

  val in = inExpression[Fact]
  val out = outExpression[Narrow.Fact]

  def randomFact(aggregateid: Uuid, aggregatelatest: Int): UIO[Fact] =
    import Random.*
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
      aggregateid <- Uuid.nextUuid
      facts <- ZIO.foreach(Range(0, n))(aggregatelatest => randomFact(aggregateid, aggregatelatest))
    yield Chunk.fromIterable(facts)
