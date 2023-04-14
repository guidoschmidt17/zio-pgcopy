package example1

import io.netty.buffer.ByteBuf
import zio.Random.*
import zio.*
import zio.pgcopy.Codec.*
import zio.pgcopy.*
import zio.stream.*

import java.time.OffsetDateTime

import Util.*

object Event:
  enum Category:
    case Created, Read, Updated, Deleted, Meta
  inline given Conversion[String, Category] = Category.valueOf(_)

case class Fact(
    serialid: Long | Null,
    created: OffsetDateTime | Null,
    aggregateid: Uuid | Null,
    aggregatelatest: Int,
    eventcategory: Event.Category,
    eventid: Uuid,
    eventdatalength: Int,
    eventdata: Array[Byte],
    tags: Array[String],
    big: BigDecimal
)

given Codec[Fact] = new Codec[Fact]:
  def apply(a: Fact)(using ByteBuf): Unit =
    import a.*
    fields(8) // check!
    uuid(aggregateid)
    int4(aggregatelatest)
    text(eventcategory)
    uuid(eventid)
    int4(eventdatalength)
    bytea(eventdata)
    _text(tags)
    numeric(big)

  def apply()(using ByteBuf): Fact =
    val res = Fact(null, null, null, int4(), text(), uuid(), int4(), bytea(), _text(), numeric())
    println(res)
    res

object Fact:

  def randomFact(aggregateid: Uuid, aggregatelatest: Int): UIO[Fact] =
    for
      ec <- nextIntBounded(4)
      eventid <- nextUUID
      eventdatalength <- nextIntBetween(5, 100)
      eventdata <- nextBytes(eventdatalength)
      tags = Array("bla", "blabla")
    yield Fact(
      null,
      null,
      aggregateid,
      aggregatelatest,
      Event.Category.fromOrdinal(ec),
      eventid,
      eventdatalength,
      eventdata.toArray,
      tags,
      // BigDecimal("17")
      BigDecimal("-9834754923857938572934857239485739847597345.12345")
      // BigDecimal("3.1452e-21")
    )

  def randomFacts(n: Int): UIO[Chunk[Fact]] =
    for
      aggregateid <- nextUUID
      facts <- ZIO.foreach(Range(0, n))(aggregatelatest => randomFact(aggregateid, aggregatelatest))
    yield Chunk.fromIterable(facts)
