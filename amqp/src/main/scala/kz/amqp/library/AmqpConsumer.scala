package kz.amqp.library

import akka.actor.ActorRef
import com.rabbitmq.client.{ AMQP, Consumer, Envelope, ShutdownSignalException }

object AmqpConsumer {
  def apply(ref: ActorRef): AmqpConsumer = new AmqpConsumer(ref)
}

class AmqpConsumer(ref: ActorRef) extends Consumer {
  override def handleConsumeOk(consumerTag: String): Unit = ()

  override def handleCancelOk(consumerTag: String): Unit = ()

  override def handleCancel(consumerTag: String): Unit = ()

  override def handleShutdownSignal(consumerTag: String, sig: ShutdownSignalException): Unit = ()

  override def handleRecoverOk(consumerTag: String): Unit = ()

  override def handleDelivery(
    consumerTag: String,
    envelope: Envelope,
    properties: AMQP.BasicProperties,
    body: Array[Byte]
  ): Unit = {
    val message = new String(body)
    ref ! message
  }
}
