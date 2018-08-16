package scorex.network.peer

import java.net.{InetAddress, InetSocketAddress}

import com.typesafe.config.ConfigFactory
import com.wavesplatform.network.PeerDatabaseImpl
import com.wavesplatform.settings.NetworkSettings
import net.ceedubs.ficus.Ficus._
import org.scalatest.{Matchers, path}

class PeerDatabaseImplSpecification extends path.FreeSpecLike with Matchers {

  private val config1 = ConfigFactory.parseString(
    """vee.network {
      |  file = null
      |  known-peers = []
      |  peers-data-residence-time: 2s
      |}""".stripMargin).withFallback(ConfigFactory.load()).resolve()
  private val settings1 = config1.as[NetworkSettings]("vee.network")

  private val config2 = ConfigFactory.parseString(
    """vee.network {
      |  file = null
      |  known-peers = []
      |  peers-data-residence-time: 10s
      |}""".stripMargin).withFallback(ConfigFactory.load()).resolve()
  private val settings2 = config2.as[NetworkSettings]("vee.network")

  val database = new PeerDatabaseImpl(settings1)
  val database2 = new PeerDatabaseImpl(settings2)
  val host1 = "1.1.1.1"
  val host2 = "2.2.2.2"
  val address1 = new InetSocketAddress(host1, 1)
  val address2 = new InetSocketAddress(host2, 2)

  "Peer database" - {
    "new peer should not appear in internal buffer but does not appear in database" in {
      database.knownPeers shouldBe empty
      database.addCandidate(address1)
      database.randomPeer(Set()) should contain(address1)
      database.knownPeers shouldBe empty
    }

    "new peer should move from internal buffer to database" in {
      database.knownPeers shouldBe empty
      database.addCandidate(address1)
      database.knownPeers shouldBe empty
      database.touch(address1)
      database.knownPeers.keys should contain(address1)
    }

    "peer should should became obsolete after time" in {
      database.touch(address1)
      database.knownPeers.keys should contain(address1)
      sleepLong()
      database.knownPeers shouldBe empty
      database.randomPeer(Set()) shouldBe empty
    }

    "touching peer prevent it from obsoleting" in {
      database.addCandidate(address1)
      database.touch(address1)
      sleepLong()
      database.touch(address1)
      sleepShort()
      database.knownPeers.keys should contain(address1)
    }

    "blacklisted peer should disappear from internal buffer and database" in {
      database.touch(address1)
      database.addCandidate(address2)
      database.knownPeers.keys should contain(address1)
      database.knownPeers.keys should not contain address2

      database.blacklist(InetAddress.getByName(host1))
      database.knownPeers.keys should not contain address1
      database.knownPeers should be(empty)

      database.randomPeer(Set()) should contain(address2)
      database.blacklist(InetAddress.getByName(host2))
      database.randomPeer(Set()) should not contain address2
      database.randomPeer(Set()) should be(empty)
    }

    "random peer should return peers from both from database and buffer" in {
      database2.touch(address1)
      database2.addCandidate(address2)
      val keys = database2.knownPeers.keys
      keys should contain(address1)
      keys should not contain address2

      val set = (1 to 10).flatMap(i => database2.randomPeer(Set())).toSet

      set should contain(address1)
      set should contain(address2)
    }

    "filters out excluded candidates" in {
      database.addCandidate(address1)
      database.addCandidate(address1)
      database.addCandidate(address2)

      database.randomPeer(Set(address1)) should contain(address2)
    }
  }

  private def sleepLong() = Thread.sleep(2200)

  private def sleepShort() = Thread.sleep(200)

}
