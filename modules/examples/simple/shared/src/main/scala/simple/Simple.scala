package simple

import io.netty.buffer.ByteBuf
import zio.*
import zio.pgcopy.*
import zio.pgcopy.given

case class Simple(i: Int)

inline given simple: BiCodec[Simple] = BiCodec[Simple](Decoder(), Encoder(_))

object Simple:
  given Simple = Simple(0)

  val in = inExpression[Simple]
  val out = outExpression[Simple]

  def randomSimple: UIO[Simple] =
    import Random.*
    for i <- nextInt yield Simple(i)
  def randomSimples(n: Int): UIO[Chunk[Simple]] =
    for s <- ZIO.foreach(Range(0, n))(_ => randomSimple)
    yield Chunk.fromIterable(s)
