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
package com.waz

import akka.pattern.ask
import com.waz.api.ProcessActorSpec
import com.waz.model.RConvId
import com.waz.provision.ActorMessage._
import com.waz.service.BackendConfig
import org.scalatest.FeatureSpec

import scala.concurrent.Await

class QAActorSpec extends FeatureSpec with ProcessActorSpec {
  override def testBackend = BackendConfig.StagingBackend
  import com.waz.threading.Threading.Implicits.Background

  scenario("Basic login test") {
//    val ref = registerDevice("test_device")
//    Await.result(ref ? Login("dean+7@wire.com", "aqa123456"), timeout)
//    Thread.sleep(10000)
//    Await.result(ref ? SendText(RConvId("f78cf1fe-a1ce-4d50-8af4-312f8a6a0e0c"), "Lah lah lah"), timeout)
//    Thread.sleep(10000)
  }
}
