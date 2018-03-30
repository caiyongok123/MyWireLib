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

import java.io.File
import java.util.Properties
import java.util.zip.ZipFile

import com.waz.provision._
import com.waz.service.BackendConfig
import com.waz.utils.IoUtils
import org.robolectric.annotation.Config
import org.robolectric.bytecode.Setup

import scala.collection.JavaConverters._
import scala.util.Random

object RemoteActorApp extends App {

  JCE.removeCryptographyRestrictions()

  if (args.isEmpty) {
    println(
      """
        |Usage: java -jar actors-{...}.jar [ProcessName] {coordinator actor ref} {backend} {otrOnly}
      """.stripMargin)
  } else {

    val processName = args(0)
    val actorRef = args.find(_.startsWith("akka"))
    val backend = args.map(BackendConfig.byName.get).collectFirst { case Some(b) => b } .getOrElse(BackendConfig.StagingBackend)
    val otrOnly = args.collectFirst {
      case "true" => true
      case "false" => false
    } .getOrElse(false)

    val tmp = new File(new File(System.getProperty("java.io.tmpdir")), Random.nextInt.toHexString)
    tmp.mkdirs()

    IoUtils.copy(getClass.getResourceAsStream("/actor_res.jar"), new File(tmp, "res.zip"))
    val zip = new ZipFile(new File(tmp, "res.zip"))
    zip.entries.asScala foreach { entry =>
      if (entry.isDirectory)
        new File(tmp, entry.getName).mkdir()
      else {
        IoUtils.copy(zip.getInputStream(entry), new File(tmp, entry.getName))
      }
    }

    // update java.library.path
    val usrPathsField = classOf[ClassLoader].getDeclaredField("usr_paths")
    usrPathsField.setAccessible(true)
    val paths = usrPathsField.get(null).asInstanceOf[Array[String]]
    usrPathsField.set(null, (paths.toSeq :+ new File(tmp, "libs").getAbsolutePath).toArray)

    val props = new Properties()
    props.put("manifest", new File(tmp, "AndroidManifest.xml").getAbsolutePath)
    println(s"manifest: ${props.get("manifest")}")
    println(s"args: ${args.toSeq}")
    println(s"otrOnly: $otrOnly")

    Setup.CLASSES_TO_ALWAYS_DELEGATE.add(classOf[RoboProcessRunner].getName)
    new RoboProcessRunner(classOf[RemoteProcess], Some(Config.Implementation.fromProperties(props))).run(Seq("RemoteOtrProcess", processName, actorRef.getOrElse(""), backend.environment, otrOnly.toString))
  }
}

