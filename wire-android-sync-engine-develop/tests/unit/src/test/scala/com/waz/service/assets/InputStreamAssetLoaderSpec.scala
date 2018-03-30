/*
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.service.assets

import org.scalatest._

@Ignore class InputStreamAssetLoaderSpec extends FeatureSpec with Matchers with OptionValues with RobolectricTests {

//  scenario("Load completes successfully") {
//    val asset: impl.AssetForUpload = impl.AssetForUpload(AssetId(), Some("name"), Mime.Default, Some(10000L)) {
//      val data = returning(Array.ofDim[Byte](10000))(Random.nextBytes)
//      _ => new ByteArrayInputStream(data)
//    }
//    val entry = loader.load(request(asset), _ => ()).await().value
//    entry.length shouldBe 10000
//    entry.file shouldBe 'defined
//  }
//
//  scenario("Load fails") {
//    val asset = impl.AssetForUpload(AssetId(), Some("name"), Mime.Default, Some(10000L))(_ => throw new FileNotFoundException("meep"))
//    loader.load(request(asset), _ => ()).await() shouldEqual None
//  }
//
//  scenario("Load returns null") {
//    val asset = impl.AssetForUpload(AssetId(), Some("name"), Mime.Default, Some(10000L))(_ => null)
//    loader.load(request(asset), _ => ()).await() shouldEqual None
//  }
//
//  scenario("Load is cancelled before first read") {
//    val env = LatchedAsset(0)
//
//    val loading = loader.load(env.downloadKey, _ => ())
//    env.readLatch.countDown()
//    env.creationArrival.await()
//    loading.cancel()("meep")
//
//    env.creationLatch.countDown()
//    loading.await() shouldEqual None
//  }
//
//  scenario("Load is cancelled in between reads") {
//    val env = LatchedAsset(65536)
//    env.creationLatch.countDown()
//    val loading = loader.load(env.downloadKey, _ => ())
//
//    env.readArrival.await()
//    loading.cancel()("meep")
//    env.readLatch.countDown()
//
//    loading.await() shouldEqual None
//  }
//
//  case class LatchedAsset(waitAt: Int) {
//    val creationArrival = new CountDownLatch(1)
//    val creationLatch = new CountDownLatch(1)
//    val readArrival = new CountDownLatch(1)
//    val readLatch = new CountDownLatch(1)
//
//    val asset = impl.AssetForUpload(AssetId(), Some("namex"), Mime.Default, Some(1048576L)) {
//      val data = returning(Array.ofDim[Byte](1048576))(Random.nextBytes)
//
//      context => {
//        creationArrival.countDown()
//        creationLatch.await()
//        new ByteArrayInputStream(data) {
//          var progress = 0
//
//          def maybeWait() = if (progress >= waitAt && readArrival.getCount > 0) {
//            readArrival.countDown()
//            readLatch.await()
//          }
//
//          override def read(buffer: Array[Byte], byteOffset: Int, byteCount: Int): Int = {
//            maybeWait()
//            returning(super.read(buffer, byteOffset, byteCount))(n => progress += math.max(0, n))
//          }
//
//          override def read(): Int = {
//            maybeWait()
//            returning(super.read())(n => if (n >= 0) progress += 1)
//          }
//        }
//      }
//    }
//
//    val downloadKey = request(asset)
//  }
//
//  lazy val global = new MockGlobalModule()
//  lazy val loader = global.streamLoader
//  def request(asset: impl.AssetForUpload) = AssetFromInputStream(asset.cacheKey, () => asset.openDataStream(global.context))
}
