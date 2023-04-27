package simple

import zio.*
import zio.pgcopy.*
import zio.pgcopy.given

case class Simple(i: Int)

given Codec[Simple] = BiCodec(Decoder(), Encoder(_))

object Simple:
  val in = inExpression[Simple]
  val out = outExpression[Simple]

  def randomSimple: UIO[Simple] =
    import Random.*
    for i <- nextInt yield Simple(i)
  def randomSimples(n: Int): UIO[Chunk[Simple]] =
    for s <- ZIO.foreach(Range(0, n))(_ => randomSimple)
    yield Chunk.fromIterable(s)
