# `zio-pgcopy`
A library to perform very fast bulk inserts and bulk selects to a PostgreSQL database using Scala 3, ZIO 2, Netty 4.1 and the PostgreSQL wire protocol 3 with binary encoding/decoding.  
&nbsp;
## Motivation
`zio-pgcopy` is an offspring of a larger eventsourcing project. In this project we use PostgreSQL as the eventstore. After some time we realized that we basically needed two operations: bulk inserts and bulk selects. But at the best throughput possible.  

`zio-pgcopy` is highly inspired by the excellent libraries `skunk` and `clj-pgcopy` (hope it's ok to borrow the name). Both use the PostgreSQL wire protocol 3. The former supports text encoding only and does not implement the copy in/out commands. But it is extremly versatile. The latter only implements the copy-in command based on the binary codec (and is written in Clojure). Hence, we decided to build our own library (and base it on Scala 3, ZIO 2, Netty and binary codecs).

The binary encoding/decoding for most datatypes is very straightforward and for some a little quirky (eg. numeric). But it is almost always superior to the text codecs in terms of network payload and cpu processing. For more details see the section below.

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
### `Simple` example
The `Simple` example uses a relation (table) with only one `int4` column. The required Codecs (Encoder/Decoder) are generated automatically using Scala 3 tuple operations. So are the corresponding insert and select sql expressions. 
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




