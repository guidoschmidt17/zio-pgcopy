package simple

import io.netty.buffer.ByteBuf
import zio.*
import zio.pgcopy.*
import zio.pgcopy.given

import scala.runtime.Statics

case class Simple(i: Int)

inline given Codec[Simple] = BiCodec[Simple](Decoder(), Encoder(_))

object Simple:
  def randomSimple: UIO[Simple] =
    import Random.*
    for i <- nextInt yield Simple(i)
  def randomSimples(n: Int): UIO[Chunk[Simple]] =
    for s <- ZIO.foreach(Range(0, n))(_ => randomSimple)
    yield Chunk.fromIterable(s)
