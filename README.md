# `zio-pgcopy`
A library to perform very fast bulk inserts and bulk selects to a PostgreSQL database using Scala 3, ZIO 2, Netty 4.1 and the PostgreSQL wire protocol 3 with binary encoding/decoding.  
&nbsp;
## Motivation
`zio-pgcopy` is an offspring of a larger eventsourcing project. In this project we use PostgreSQL as the eventstore. After some time we realized that we basically needed two operations: bulk inserts and bulk selects. But at the best throughput possible.  

`zio-pgcopy` is highly inspired by the excellent libraries `skunk` and `clj-pgcopy` (hope it's ok to borrow the name). Both use the PostgreSQL wire protocol 3. The former supports text encoding only and does not implement the copy in/out commands. But it is extremly versatile. The latter only implements the copy-in command based on the binary codec (and is written in Clojure). Hence, we decided to build our own library (and base it on Scala 3, ZIO 2, Netty and binary codecs).

The binary encoding/decoding for most datatypes is very straightforward and for some a little quirky (eg. numeric). But it is almost always superior to the text codec in terms of network payload and cpu processing. For more details see the sections below.

With `zio-pgcopy` we managed to increase the throughput from 10000-100000 rows/sec to 1-5 million rows/sec depending on table width and column complexity.    
&nbsp;
## Features
- **API**: zio-pgcopy provides two PostgreSQL specific commands: CopyIn (bulk inserts) and CopyOut (bulk selects)
- **Performance**: It supports inserts and selects at a rate of millions of rows per second      
&nbsp;
## Setup
Add to your 'build.sbt':
```scala
libraryDependencies += "com.guidoschmidt17" %% "zio-pgcopy" % "0.1.0-RC1"
```
&nbsp;
## Usage
### API 
`zio-pgcopy` has a very small and manageable api.
```scala
import zio.*
import zio.stream.*

trait Copy:

  import Copy.MakeError

  def in[E: MakeError, A: Encoder](insert: String, rows: ZStream[Any, E, A]): IO[E, Unit]

  def out[E: MakeError, A: Decoder](select: String, limit: Long = Long.MaxValue): ZIO[Scope, E, ZStream[Any, E, Chunk[A]]]
``` 
As a user you need to provide a `given` instance of a `MakeError` (Any => E). Using a `Throwable` for `E` enables stack traces.
```scala
given MakeError[String] = _.toString
```
Then you need a case class for type `A` which matches the table in your PootgreSQL database. If the case class follows some rules of convention then the `Encoder` and `Decoder` as well as the `insert` and `select` `sql` will be generated automatically. 
These conventional rules are as follows:
- The case class name in lowercase must match the PostgreSQL relation name. If not, you need to provide the relation name with exact case.
- The case class variable names must match the field names of the relation in PostgreSQL in exact case and the order of variables and fields must match. 
- The case class variables must map to their corresponding PostgreSQL data type (eg. `String` -> `text`, `Long` -> `int8`, `BigDecimal` -> `numeric`). You'll find all supported mappings below.
- Fields that get filled by PostgreSQL on insert (eg. BigSerial or a 'now' timestamptz) must be omitted.
- Null values/fields are not supported.
  
&nbsp;
### `Simple` example
The `Simple` example uses a table with only one `int4` column. The required Codecs (Encoder/Decoder) are generated automatically using Scala 3 tuple operations. So are the corresponding insert and select sql expressions. 
```sql
create unlogged table simple (
  i int4 not null
  ) with (autovacuum_enabled = off);
```
Declare a case class and its codec:
```scala
case class Simple(i: Int)

given Codec[Simple] = BiCodec(Decoder(), Encoder(_))

object Simple:
  val in = inExpression[Simple]
  val out = outExpression[Simple]
```
And use it:
```scala
def run =
  import Simple.*
  for
    data <- randomSimples(n)
    loop = for
      _ <- copy.in(in, ZStream.fromChunk(data)).measured(s"copy.in")
      _ <- ZIO.scoped(copy.out(out, n).flatMap(_.runDrain).measured(s"copy.out"))
    yield ()
    _ <- loop.repeatN(repeats)
  yield ()

// results: in: 10.3 / out: 4.1 / in/out: 6.3 (mio ops/sec)
```
&nbsp;
### `Facts` example
The `Facts` example uses a more realistic table which is similar to what we use in our eventsourcing system. The required Codecs (Encoder/Decoder) are generated automatically using Scala 3 tuple operations. So are the corresponding insert and select sql expressions. 
```sql
-- types

create type eventcategory as enum('Created', 'Read', 'Updated', 'Deleted', 'Meta');

-- tables, indexes

create unlogged table fact (
  serialid bigserial primary key, 
  created timestamptz not null default now(),
  aggregateid uuid not null,
  aggregatelatest int4 not null,
  eventcategory eventcategory not null,
  eventid uuid not null,
  eventdatalength int4 not null,
  eventdata bytea not null,
  tags text[] not null
  ) with (autovacuum_enabled = off);

```
Declare a case class and its codec:
```scala
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

given Codec[Fact] = BiCodec(Decoder(), Encoder(_))

object Fact:
  val in = inExpression[Fact]   // fact(aggregateid,aggregatelatest,eventcategory,eventid,eventdatalength,eventdata,tags)
  val out = outExpression[Fact] // select aggregateid,aggregatelatest,eventcategory,eventid,eventdatalength,eventdata,tags from fact

```
And use it:
```scala
def run =
  import Fact.*
  for
    data <- randomFacts(n)
    warmup = for
      _ <- copy.in(in, ZStream.fromChunk(data).rechunk(32 * 1024))
      _ <- ZIO.scoped(copy.out[String, Narrow.Fact](out, n).flatMap(_.runDrain))
    yield ()
    _ <- warmup.repeatN(warmups)
    _ = begin.set(nanoTime)
    i <- Random.nextIntBetween(1, 500)
    _ <- ZIO.sleep(i.milliseconds)
    loop = for
      _ <- copy.in(in, ZStream.fromChunk(data).rechunk(32 * 1024)).measured(s"copy.in")
      _ <- ZIO.scoped(copy.out[String, Narrow.Fact](out, n).flatMap(_.runDrain).measured(s"copy.out"))
    yield lap
    _ <- loop.repeatN(repeats)
  yield ()

  // results: in: 0.9 / out: 2.3 / in/out: 1.4 (mio ops/sec)
```
&nbsp;
## Mappings
### Mapping to and from Scala and PostgreSQL data types
```scala 
  Boolean <-> bool
  Array[Byte] <-> bytea
  Char <-> char(1) 
  String <-> text
  String <-> varchar
  String <-> name
  String <-> json
  String <-> jsonb
  Short <-> int2
  Int <-> int4
  Long <-> int8
  Float <-> float4
  Double <-> float8
  BigDecimal <-> numeric
  LocalDate <-> date
  OffsetDateTime <-> timestamptz
  OffsetDateTime <-> timestamp
  Util.Interval <-> interval
  Util.Uuid <-> uuid

  Array[<all-of-the-above>] <-> _<all-of-the-above>
```
&nbsp;
### Codecs for PostgreSQL data types
`zio-pgcopy` supports the most commonly used PostgreSQL data types (in our opinion). If a data type is not mapped the `text` codec is used as a fallback. You can provide a `text` to and from `your-type` conversion to support `your-type`. This can be used for Scala 3 enums, for instance. The provided codecs are used to compose a decoder/encoder automatically or manually if the automatic codec generation is not possible because the necessary preconditions are not met. 

```scala
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
```

&nbsp;
## Configuration
```yaml
zio-pgcopy:
  server:
    host           : localhost
    port           : 5432
    sslmode        : disable # disable | trust | runtime 
    database       : facts
    user           : jimmy
    password       : banana

  pool:
    min            : 32
    max            : 32
    timeout        : 15.minutes

  retry:
    base           : 200.milliseconds
    factor         : 1.33
    retries        : 5

  io:
    so_sndbuf      : 32768  
    so_rcvbuf      : 32768
    bytebufsize    : 8000000
    checkbufsize   : false  
    incomingsize   : 4096
    outgoingsize   : 4096
```

