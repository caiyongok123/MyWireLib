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
package com.waz.model

import android.util.Base64
import com.waz.utils.{JsonDecoder, JsonEncoder}
import org.json.JSONObject
import com.wire.cryptobox.PreKey

package object otr {

  implicit lazy val PreKeyEncoder: JsonEncoder[PreKey] = new JsonEncoder[PreKey] {
    override def apply(v: PreKey): JSONObject = JsonEncoder { o =>
      o.put("id", v.id)
      o.put("key", Base64.encodeToString(v.data, Base64.NO_WRAP | Base64.NO_CLOSE))
    }
  }

  implicit lazy val PreKeyDecoder: JsonDecoder[PreKey] = new JsonDecoder[PreKey] {
    override def apply(implicit js: JSONObject): PreKey = new PreKey(js.getInt("id"), Base64.decode(js.getString("key"), Base64.NO_WRAP | Base64.NO_CLOSE))
  }
}
