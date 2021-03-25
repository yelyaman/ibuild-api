package kz.amqp.library

import java.util

import com.rabbitmq.client.AMQP.{Exchange, Queue}
import com.rabbitmq.client.{Channel, Connection, ConnectionFactory}

import scala.util.{Failure, Success, Try}

object RabbitMqConnection {

  def getRabbitMqConnection(username: String,
                            password: String,
                            host: String,
                            port: Int,
                            virtualHost: String): Connection ={
    val factory = new ConnectionFactory
    factory.setUsername(username)
    factory.setPassword(password)
    factory.setHost(host)
    factory.setPort(port)
    factory.setVirtualHost(virtualHost)

    factory.newConnection()
  }

  def declareExchange(channel: Channel, exchangeName: String, `type`: String): Try[Exchange.DeclareOk] =
    Try(
      channel.exchangeDeclare(
        exchangeName,
        `type`,
        true,
        false,
        new util.HashMap[String, AnyRef]
      )
    )

  def declareAndBindQueue(channel: Channel, queueName: String, exchangeName: String, routingKey: String): Try[Queue.BindOk] =
    Try(
      channel.queueDeclare(
        queueName,
        true,
        true,
        true,
        new util.HashMap[String, AnyRef]
      )
    ) match {
      case Success(_) =>
        Try(channel.queueBind(queueName, exchangeName, routingKey))
      case Failure(exception) => Failure(exception)
    }
}
