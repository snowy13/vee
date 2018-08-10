package scorex.lagonaki.unit

import com.wavesplatform.state2.ByteStr
import org.scalamock.scalatest.MockFactory
import org.scalatest.{FunSuite, Matchers}
import scorex.account.PrivateKeyAccount
import scorex.block.Block
import vee.consensus.spos.SposConsensusBlockData
import scorex.transaction._
import scorex.transaction.assets.TransferTransaction

import scala.util.Random

class BlockSpecification extends FunSuite with Matchers with MockFactory {

  test(" block with txs bytes/parse roundtrip") {

    val reference = Array.fill(Block.BlockIdLength)(Random.nextInt(100).toByte)
    val gen = PrivateKeyAccount(reference)

    val mt = System.currentTimeMillis() / 10000 * 10000000000000L
    val mb = 100000000000L
    val gs = Array.fill(Block.GeneratorSignatureLength)(Random.nextInt(100).toByte)


    val ts = System.currentTimeMillis() * 1000000L + System.nanoTime() % 1000000L - 5000000000L
    val sender = PrivateKeyAccount(reference.dropRight(2))
    val tx: Transaction = PaymentTransaction.create(sender, gen, 5, 1000, 100, ts).right.get
    val tr: TransferTransaction = TransferTransaction.create(None, sender, gen, 5, ts + 1, None, 2, Array()).right.get
    val assetId = Some(ByteStr(Array.fill(AssetIdLength)(Random.nextInt(100).toByte)))
    val tr2: TransferTransaction = TransferTransaction.create(assetId, sender, gen, 5, ts + 2, None, 2, Array()).right.get

    val tbd = Seq(tx, tr, tr2)
    val cbd = SposConsensusBlockData(mt, mb, gs)

    List(1, 2).foreach { version =>
      val timestamp = System.currentTimeMillis() * 1000000L + System.nanoTime() % 1000000L
      val block = Block.buildAndSign(version.toByte, timestamp, ByteStr(reference), cbd, tbd, gen)
      val parsedBlock = Block.parseBytes(block.bytes).get
      assert(Signed.validateSignatures(block).isRight)
      assert(Signed.validateSignatures(parsedBlock).isRight)
      assert(parsedBlock.consensusData.generationSignature.sameElements(gs))
      assert(parsedBlock.version.toInt == version)
      assert(parsedBlock.signerData.generator.publicKey.sameElements(gen.publicKey))
      assert(parsedBlock.transactionData.size == 3)
      assert(parsedBlock.transactionData(0).status == TransactionStatus.Success)
      assert(parsedBlock.transactionData(0).feeCharged == 1000)
      assert(parsedBlock.transactionData(1).status == TransactionStatus.Success)
      assert(parsedBlock.transactionData(1).feeCharged == 2)
      assert(parsedBlock.transactionData(2).status == TransactionStatus.Success)
      assert(parsedBlock.transactionData(2).feeCharged == 2)
    }
  }
}
