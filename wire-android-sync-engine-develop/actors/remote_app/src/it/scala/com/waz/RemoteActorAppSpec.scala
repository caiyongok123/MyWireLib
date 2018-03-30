package com.waz

import java.io.{File, PrintWriter}

import akka.actor.ActorRef
import akka.pattern.ask
import akka.serialization.Serialization
import com.waz.api.impl.Conversation
import com.waz.api.{ProcessActorSpec, ProvisionedApiSpec}
import com.waz.provision.ActorMessage._
import com.waz.testutils.Matchers._
import com.waz.testutils.Implicits._
import org.robolectric.annotation.Config
import org.scalatest.{BeforeAndAfterAll, FeatureSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._

@Config(manifest = "src/main/AndroidManifest.xml")
class RemoteActorAppSpec extends FeatureSpec with Matchers with BeforeAndAfterAll with ProvisionedApiSpec with ProcessActorSpec { test =>

  override val provisionFile = "/two_users_connected.json"

  lazy val convs = api.getConversations
  lazy val conv = {
    withDelay { convs should not be empty }
    convs.get(0)
  }
  lazy val msgs = conv.getMessages

  lazy val appJar = {
    new File("target").listFiles().find(_.getName.startsWith("remote-actor-")).get
  }

  lazy val process = {
    import scala.sys.process._
    val serialized = Serialization.serializedActorPath(coordinatorActor)

    val cmd = s"java -jar ${appJar.getAbsolutePath} remote_actor $serialized staging true"
    val outputFile = new File(s"target/logcat/RemoteActorAppSpec_remote")
    outputFile.getParentFile.mkdirs()
    val printWriter = new PrintWriter(outputFile)

    val writeToStream: String => Unit =
      line => {
        printWriter.println(line)
        printWriter.flush()
      }

    val processLogger = ProcessLogger(writeToStream, writeToStream)

    new Thread() {
      override def run(): Unit = cmd ! processLogger
    }.start()

    Await.result(coordinatorActor.ask(WaitUntilRegistered("remote_actor")).mapTo[ActorRef], 15.seconds)
  }

  lazy val auto2 = registerDevice("otr_auto2", process)
  lazy val auto2_1 = registerDevice("otr_auto2_1", process)

  scenario("init") {
    val client = api.getSelf.getOtrClient
    withDelay {
      convs should not be empty
      client should not be empty
    }
    info(s"client: ${client.get.getId}")
  }

  scenario("init remote processes") {
    auto2 ? Login(provisionedEmail("auto2"), "auto2_pass") should eventually(be(Successful))
  }

  scenario("receive message from remote") {
    auto2 ? SendText(conv.asInstanceOf[Conversation].data.remoteId, "Remote message") should eventually(be(Successful))

    withDelay {
      msgs.getLastMessage.getBody shouldEqual "Remote message"
    }
  }

  scenario("init second remote device on same process") {
    auto2_1 ? Login(provisionedEmail("auto2"), "auto2_pass") should eventually(be(Successful))
  }

  scenario("receive message from second device") {
    auto2_1 ? SendText(conv.asInstanceOf[Conversation].data.remoteId, "Remote message 1") should eventually(be(Successful))

    withDelay {
      msgs.getLastMessage.getBody shouldEqual "Remote message 1"
    }
  }

  scenario("List messages from remote") {
    val res = (auto2_1 ? GetMessages(conv.asInstanceOf[Conversation].data.remoteId)).await()

    res match {
      case ConvMessages(ms) if ms.nonEmpty =>
        info(s"got messages: ${ms.toSeq}")
        true
      case _ => fail(s"unexpected: $res")
    }
  }
}
