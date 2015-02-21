package mypipe.kafka

import mypipe.api.event.Mutation

object KafkaUtil {

  def genericTopic(mutation: Mutation[_]): String =
    genericTopic(mutation.table.db, mutation.table.name)

  def genericTopic(db: String, table: String) =
    s"${db}_generic"

  def specificTopic(mutation: Mutation[_]): String =
    specificTopic(mutation.table.db, mutation.table.name)

  def specificTopic(db: String, table: String): String =
    s"${db}_specific"
}
