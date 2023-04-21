package object simple:

  import zio.*
  import java.nio.charset.StandardCharsets.UTF_8

  final def readResourceFile(name: String): String =
    String(getClass.nn.getClassLoader.nn.getResourceAsStream(name).nn.readAllBytes, UTF_8)

  extension [R, E, A](f: ZIO[R, E, A])
    def measured(prefix: String): ZIO[R, E, A] =
      for
        (d, r) <- f.timed
        sec = d.getSeconds + d.getNano / 1000000000.0
        _ <- (if prefix.length > 0 then ZIO.debug(s"$prefix : ${sec}s") else ZIO.debug(s"${sec}s"))
      yield r
    def measured: ZIO[R, E, A] = f.measured("".intern.nn)
