package example1

import io.netty.buffer.ByteBuf
import zio.Random.*
import zio.*
import zio.pgcopy.*
import zio.stream.*

import java.time.OffsetDateTime

import Codec.*
import Util.*

object Event:
  enum Category:
    case Created, Read, Updated, Deleted, Meta

case class Fact(
    serialid: Long | Null,
    created: OffsetDateTime | Null,
    aggregateid: Uuid | Null,
    aggregatelatest: Int,
    eventcategory: Event.Category,
    eventid: Uuid,
    eventdatalength: Int,
    eventdata: Array[Byte],
    tags: Array[String]
)

object Fact:

  def randomFact(aggregateid: Uuid, aggregatelatest: Int): UIO[Fact] =
    for
      ec <- nextIntBounded(4)
      eventid <- nextUUID
      eventdatalength <- nextIntBetween(5, 100)
      eventdata <- nextBytes(eventdatalength)
      tags = Array("bla", "blabla")
    yield Fact(null, null, aggregateid, aggregatelatest, Event.Category.fromOrdinal(ec), eventid, eventdatalength, eventdata.toArray, tags)

  def randomFacts(n: Int): UIO[Chunk[Fact]] =
    for
      aggregateid <- nextUUID
      facts <- ZIO.foreach(Range(0, n))(aggregatelatest => randomFact(aggregateid, aggregatelatest))
    yield Chunk.fromIterable(facts)

  given Encoder[Fact] = new Encoder[Fact]:
    def apply(a: Fact)(using ByteBuf): Unit =
      fields(7)
      import a.*
      uuid(aggregateid)
      int4(aggregatelatest)
      text(eventcategory)
      uuid(eventid)
      int4(eventdatalength)
      bytea(eventdata)
      _text(tags)

  given Decoder[Fact] = new Decoder[Fact]:
    def apply()(using ByteBuf): Fact =
      // Fact(int8(), timestamptz(), uuid(), int4(), Event.Category.valueOf(text()), uuid(), int4(), bytea(), _text())
      Fact(null, null, null, int4(), Event.Category.valueOf(text()), uuid(), int4(), bytea(), _text())

  val xxx = int8 *: timestamptz *: EmptyTuple
