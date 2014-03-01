package mypipe

import mypipe.mysql.{ BinlogConsumer, BinlogFilePos, HostPortUserPass }
import scala.collection.JavaConverters._
import mypipe.api.{ Mapping, Producer }

object Mypipe extends App {

  val producers: List[Producer] = Conf.PRODUCERS.map(kv ⇒ {
    val name = kv._1
    Log.info(s"Loading configuration for producers $name")

    val conf = Conf.conf.getConfig(s"mypipe.producers.$name")
    val clazz = conf.getString("class")
    val enabled = if (conf.hasPath("enabled")) conf.getBoolean("enabled") else true

    if (enabled) {
      val mappings = if (conf.hasPath("mappings")) conf.getStringList("mappings").asScala else List[String]()

      Log.info(s"  $clazz requires the following mappings: $mappings")

      try {
        val m = mappings.map(mappingClass ⇒ Class.forName(mappingClass).newInstance()).toList
        val producer = Class.forName(clazz).getConstructor(classOf[List[Mapping]]).newInstance(m)
        producer
      } catch {
        case e: Exception ⇒ {
          Log.severe(s"Failed to configure producer $name: ${e.getMessage}\n${e.getStackTraceString}")
          null
        }
      }

    } else {
      // disabled
      null
    }
  }).toList.filter(_ != null).asInstanceOf[List[Producer]]

  if (producers.isEmpty) {
    Log.info("No producers defined, exiting.")
    sys.exit()
  }

  val consumers = Conf.sources.asScala.map(
    source ⇒ {
      val params = HostPortUserPass(source)
      val filePos = Conf.binlogFilePos(params.host, params.port).getOrElse(BinlogFilePos.current)
      val consumer = BinlogConsumer(params.host, params.port, params.user, params.password, filePos)
      producers.foreach(producer ⇒ consumer.registerProducer(producer))
      consumer
    })

  sys.addShutdownHook({
    Log.info("Shutting down...")
    consumers.foreach(c ⇒ c.disconnect())
  })

  val threads = consumers.map(c ⇒ new Thread() { override def run() { c.connect() } })
  threads.foreach(_.start())
  threads.foreach(_.join())
}
