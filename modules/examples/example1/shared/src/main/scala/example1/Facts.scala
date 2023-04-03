package example1

import io.netty.buffer.ByteBuf
import zio.Random.*
import zio.*
import zio.pgcopy.*
import zio.stream.*

import java.time.OffsetDateTime

import Codec.*
import util.Uuid

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

  final given Encoder[Fact] = new Encoder[Fact]:
    inline def apply(a: Fact)(using ByteBuf): Unit =
      fields(7)
      import a.*
      uuid(aggregateid)
      int4(aggregatelatest)
      text(eventcategory.toString)
      uuid(eventid)
      int4(eventdatalength)
      bytea(eventdata)
      _text(tags)

  final given Decoder[Fact] = new Decoder[Fact]:
    inline def apply()(using ByteBuf): Fact =
      Fact(null, null, null, int4(), Event.Category.valueOf(text()), uuid(), int4(), bytea(), _text())
//      Fact(int8(), timestamptz(), uuid(), int4(), Event.Category.valueOf(text()), uuid(), int4(), bytea(), _text())
