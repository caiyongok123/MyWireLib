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
package com.waz.androidactors

import akka.util.Timeout
import android.app.Activity
import android.os.Bundle
import android.text.{Editable, TextWatcher}
import android.view.View
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.CompoundButton.OnCheckedChangeListener
import android.widget._
import com.waz.service.BackendConfig
import com.waz.threading.Threading
import com.waz.utils.events.ActivityEventContext

import scala.concurrent.duration._

class MainActivity extends Activity with ActivityEventContext {
  private implicit val timeout = Timeout(15.seconds)

  lazy val etName = findViewById(R.id.etName).asInstanceOf[EditText]
  lazy val spBackend = findViewById(R.id.spBackend).asInstanceOf[Spinner]
  lazy val cbBackground = findViewById(R.id.cbBackground).asInstanceOf[CheckBox]
  lazy val tvStatus = findViewById(R.id.tvStatus).asInstanceOf[TextView]

  lazy val service = getApplication.asInstanceOf[ActorsApplication].remoteActor

  val backends = Array(BackendConfig.StagingBackend, BackendConfig.ProdBackend)

  override def onCreate(savedInstanceState: Bundle) = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.main)

    Threading.AssertsEnabled = false

    spBackend.setAdapter(new ArrayAdapter[String](this, android.R.layout.simple_list_item_1, backends.map(_.environment)))

    service.name.signal.onUi { name =>
      if (etName.getText.toString.isEmpty) etName.setText(name)
    }
    service.backend.onUi { b => spBackend.setSelection(backends.indexOf(b)) }

    service.actorState.onUi { st => tvStatus.setText(st.toString) }

    service.background.signal.onUi { cbBackground.setChecked }

    etName.addTextChangedListener(new TextWatcher {
      override def beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int): Unit = ()

      override def onTextChanged(s: CharSequence, start: Int, before: Int, count: Int): Unit = service.name := s.toString

      override def afterTextChanged(s: Editable): Unit = ()
    })

    spBackend.setOnItemSelectedListener(new OnItemSelectedListener {
      override def onNothingSelected(parent: AdapterView[_]): Unit = ()

      override def onItemSelected(parent: AdapterView[_], view: View, position: Int, id: Long): Unit = service.backendPref := backends(position).environment
    })

    cbBackground.setOnCheckedChangeListener(new OnCheckedChangeListener {
      override def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit = service.background := isChecked
    })
  }
}
