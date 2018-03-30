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
package com.waz.service.images

import com.waz.specs.AndroidFreeSpec

class ImageLoaderAndroidFreeSpec extends AndroidFreeSpec {

//  import com.waz.ui.MemoryImageCache.{Entry, Key}
//
//  import scala.concurrent.ExecutionContext.Implicits.global
//
//  val TIMEOUT = 5.seconds
//  val ARB_DATA_SIZE = 10
//
//  lazy val alphaNumStr = Gen.nonEmptyListOf(alphaNumChar).map(_.mkString)
//  def sideEffect[A](f: => A): Gen[A] = Gen.resultOf[Unit, A](_ => f)
//
//  implicit def optGen[T](implicit gen: Gen[T]): Gen[Option[T]] = Gen.frequency((1, Gen.const(None)), (2, gen.map(Some(_))))
//
//  implicit lazy val arbAssetId: Arbitrary[AssetId] = Arbitrary(sideEffect(AssetId()))
//  implicit lazy val arbRConvId: Arbitrary[RConvId] = Arbitrary(sideEffect(RConvId()))
//
//  implicit lazy val arbUri: Arbitrary[URI] = Arbitrary(for {
//    scheme <- Gen.oneOf("file", "content", "http")
//    path <- alphaNumStr
//  } yield URI.parse(s"$scheme://$path"))
//
//  private def arbAssetData(mimeTypes: List[Mime] = Mime.Image.supported.toList,
//                           sourceGen: Gen[Option[URI]] = optGen(arbitrary[URI])
//                          ): List[AssetData] = {
//    val arbData: Arbitrary[AssetData] = Arbitrary(for {
//      id            <- arbitrary[AssetId]
//      mime          <- Gen.oneOf(mimeTypes)
//      sizeInBytes   <- Gen.posNum[Long]
//      name          <- optGen(alphaNumStr)
//      source        <- sourceGen
//      proxyPath     <- optGen(arbitrary[String])
//      convId        <- optGen(arbitrary[RConvId])
//    } yield AssetData(id, mime, sizeInBytes, UploadNotStarted, None, None, None, None, None, name, None, None, source, proxyPath, convId, None))
//    (1 to ARB_DATA_SIZE).flatMap(_ => arbData.arbitrary.sample).toList
//  }
//
//  private def arbImgAssetData: List[AssetData] = arbAssetData()
//  private def arbImgNonLocalAssetData: List[AssetData] = arbAssetData(sourceGen = optGen(alphaNumStr.map(s => URI.parse(s"http://$s"))))
//
//  val context = stub[Context]
//  val database = stub[Database]
//  val cacheStorage = stub[CacheStorage]
//  val cacheService = CacheService(context, database, cacheStorage)
//  val memoryImageCache = new MemoryImageCache(stub[Cache[Key, Entry]])
//  val bitmapDecoder = new BitmapDecoder
//  val permissionsService = new PermissionsService(context)
//  val assetLoader = stub[AssetLoader]
//  val assetLoadService = stub[AssetLoaderService]
//
//  private def waitForResult[T](f: => Future[T]): T = Await.result(f, TIMEOUT)
//
//  feature("has cached bitmaps") {
//
//    scenario("confirms it does not have a cached bitmap") {
//      val loader = new ImageLoader(context, cacheService, memoryImageCache, bitmapDecoder, permissionsService, assetLoadService, assetLoader)
//
//      arbImgAssetData.foreach( asset => {
//        val req = Regular(asset.width)
//        waitForResult { loader.hasCachedBitmap(asset, req) } should be(false)
//      })
//    }
//
//    scenario("confirms it has a cached bitmap of the given size") {
//      arbImgAssetData.foreach( asset => {
//        val cache = mock[Cache[Key, Entry]]
//        val bmp = FakeBitmap(1, asset.width, asset.height, false)
//        val req = Regular(asset.width)
//        (cache.get _).expects(Key(asset.id, MemoryImageCache.tag(req))).returning(BitmapEntry(bmp))
//
//        val loader = new ImageLoader(context, cacheService, new MemoryImageCache(cache), bitmapDecoder, permissionsService, assetLoader)
//
//        waitForResult { loader.hasCachedBitmap(asset, req) } should be(true)
//      })
//    }
//
//    scenario("confirms it has a cached bitmap bigger than needed") {
//      arbImgAssetData.foreach( asset => {
//        val cache = mock[Cache[Key, Entry]]
//        val bmp = FakeBitmap(1, asset.width + 1, asset.height, false)
//        val req = Regular(asset.width)
//        (cache.get _).expects(Key(asset.id, MemoryImageCache.tag(req))).returning(BitmapEntry(bmp))
//
//        val loader = new ImageLoader(context, cacheService, new MemoryImageCache(cache), bitmapDecoder, permissionsService, assetLoader)
//
//        waitForResult { loader.hasCachedBitmap(asset, req) } should be(true)
//      })
//
//    }
//  }
//
//  feature("has cached data") {
//    scenario("does not store non-image assets") {
//      val loader = new ImageLoader(context, cacheService, memoryImageCache, bitmapDecoder, permissionsService, assetLoader)
//
//      arbAssetData(mimeTypes = List(Mime.Default) ++ Mime.Video.supported ++ Mime.Audio.supported).foreach( asset => {
//        val req = Regular(asset.width)
//        waitForResult { loader.hasCachedData(asset) } should be(false)
//      })
//    }
//
//    scenario("always confirms it has local data") {
//      val loader = new ImageLoader(context, cacheService, memoryImageCache, bitmapDecoder, permissionsService, assetLoader)
//
//      arbAssetData(sourceGen = alphaNumStr.map(s => Option(URI.parse(s"file://$s")))).foreach( asset => {
//        val req = Regular(asset.width)
//        waitForResult { loader.hasCachedData(asset) } should be(true)
//      })
//    }
//
//    scenario("has cached non-local data") {
//      arbImgNonLocalAssetData.foreach( asset => {
//        val cacheStorage = mock[CacheStorage]
//        (cacheStorage.get _).expects(*).returning(Future(Option(CacheEntryData(asset.cacheKey))))
//
//        val loader = new ImageLoader(context, CacheService(context, database, cacheStorage), memoryImageCache, bitmapDecoder, permissionsService, assetLoader)
//
//        val req = Regular(asset.width)
//        waitForResult { loader.hasCachedData(asset) } should be(true)
//      })
//    }
//  }
//
//  feature("image downloading") {
//    scenario("loads memory-cached bitmap") {
//      arbImgAssetData.foreach(asset => {
//        val cache = mock[Cache[Key, Entry]]
//        val bmp = FakeBitmap(getWidth = 128, getHeight = 128)
//        val req = Regular(128)
//        (cache.get _).expects(Key(asset.id, MemoryImageCache.tag(req))).returning(BitmapEntry(bmp))
//
//        val loader = new ImageLoader(context, cacheService, new MemoryImageCache(cache), bitmapDecoder, permissionsService, assetLoader)
//
//        waitForResult { loader.loadBitmap(asset, req) } shouldEqual (bmp)
//      })
//    }
//
//    scenario("loads file-cached bitmap") {
//      arbImgNonLocalAssetData.foreach( asset => {
//        val bmp = FakeBitmap(getWidth = 128, getHeight = 128)
//        val req = Regular(128)
//        val key = Key(asset.id, MemoryImageCache.tag(req))
//        val cacheEntryData = CacheEntryData(asset.cacheKey)
//
//        val memoryCache = mock[Cache[Key, Entry]]
//        val fileCache = mock[CacheStorage]
//        val bitmapDecoder = mock[BitmapDecoder]
//        val assetLoader = stub[AssetLoader]
//
//        inAnyOrder {
//          (memoryCache.get _).expects(key).twice().returning(null)
//          (fileCache.get _).expects(asset.cacheKey).once().returning( Future( Some { cacheEntryData }) )
//          (bitmapDecoder.apply(_: () => InputStream, _: Int, _: Int)).expects(*, *, *).returning( CancellableFuture successful { bmp } )
//          (memoryCache.put _).expects(key, *).twice().onCall { (key: Key, value: Entry) => value }
//        }
//
//        val loader = new ImageLoader(context, CacheService(context, database, fileCache), new MemoryImageCache(memoryCache), bitmapDecoder, permissionsService, assetLoader) {
//          override def getImageMetadata(data: LocalData, mirror: Boolean) = CancellableFuture {
//            Metadata(bmp.getWidth, bmp.getHeight, Mime.Image.Bmp.str)
//          }
//        }
//
//        waitForResult { loader.loadBitmap(asset, req) } shouldEqual (bmp)
//      })
//    }
//
//    scenario("download a bitmap") {
//      arbImgNonLocalAssetData.foreach( asset => {
//        val bmp = FakeBitmap(getWidth = 128, getHeight = 128)
//        val req = Regular(128)
//        val key = Key(asset.id, MemoryImageCache.tag(req))
//        val cacheEntryData = CacheEntryData(asset.cacheKey)
//
//        val memoryCache = mock[Cache[Key, Entry]]
//        val fileCache = mock[CacheStorage]
//        val bitmapDecoder = mock[BitmapDecoder]
//        val assetLoader = mock[AssetLoader]
//
//        inAnyOrder {
//          (memoryCache.get _).expects(key).twice().returning(null)
//          (fileCache.get _).expects(asset.cacheKey).once().returning( Future(None) )
//          (bitmapDecoder.apply(_: () => InputStream, _: Int, _: Int)).expects(*, *, *).returning( CancellableFuture { bmp } )
//          (memoryCache.put _).expects(key, *).twice().onCall { (key: Key, value: Entry) => value }
//          (assetLoader.downloadAssetData _).expects(asset.loadRequest).returning( CancellableFuture { Some(new CacheEntry(cacheEntryData, cacheService)) } )
//        }
//
//        val loader = new ImageLoader(context, CacheService(context, database, fileCache), new MemoryImageCache(memoryCache), bitmapDecoder, permissionsService, assetLoader) {
//          override def getImageMetadata(data: LocalData, mirror: Boolean): CancellableFuture[Metadata] = CancellableFuture {
//            Metadata(bmp.getWidth, bmp.getHeight, Mime.Image.Bmp.str)
//          }
//        }
//
//        waitForResult { loader.loadBitmap(asset, req) } shouldEqual (bmp)
//      })
//    }
//  }

}
