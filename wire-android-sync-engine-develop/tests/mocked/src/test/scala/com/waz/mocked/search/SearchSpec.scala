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
package com.waz.mocked.search

import com.waz.api._
import com.waz.mocked.MockBackend
import com.waz.model.SearchQuery.TopPeople
import com.waz.model._
import com.waz.service.SearchKey
import com.waz.sync.client.AddressBookClient.UserAndContactIds
import com.waz.sync.client.UserSearchClient.UserSearchEntry
import com.waz.testutils.HasId._
import com.waz.testutils.Implicits._
import com.waz.testutils.Matchers._
import com.waz.testutils._
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.Locales._
import com.waz.utils._
import com.waz.znet.ZNetClient._
import org.scalatest.concurrent.ScaledTimeSpans
import org.scalatest.{FeatureSpec, Inspectors}
import org.threeten.bp.Instant.now

import scala.collection.breakOut
import scala.concurrent.duration._

class SearchSpec extends FeatureSpec with Inspectors with ScaledTimeSpans with MockedClientApiSpec with MockBackend {
  import DefaultPushBehaviour.Implicit

  override lazy val selfUserId = UserId("thisisme")

  val self = UserInfo(selfUserId, Some("Cartoon Consumer"))
  val meeper = UserInfo(UserId("meeper"), Some("Road Runner"))
  val coyote = UserInfo(UserId("coyote"), Some("Wile E. Coyote"))
  val elmer = UserInfo(UserId("elmer"), Some("Elmer Fudd")).copy(email = Some(EmailAddress("huntsman@thewoods.geo")))
  val bugs = UserInfo(UserId("bugs"), Some("Bugs Bunny"))
  val carrot = UserInfo(UserId("carrot"), Some("Carrot"))

  val recommended = 1 to 30 map { idx => UserInfo(UserId(s"user-$idx"), Some(s"User $idx"), email = Some(EmailAddress(s"user-$idx@dev.null"))) }

  object generated {
    val coyoteAndElmer = addGroupConversation(idsOfAll(elmer, coyote), id = RConvId("a"))
    val meeperAndBugs = addGroupConversation(idsOfAll(meeper, bugs), id = RConvId("b"))
    val bugsAndCarrot = addGroupConversation(idsOfAll(bugs, carrot), id = RConvId("c"))
  }

  object named {
    val neitherNameNorMembersMatch = addGroupConversation(idsOfAll(meeper, bugs), id = RConvId("d"), name = Some("some inconspicuous name"))
    val nameMatchesButMembersDont = addGroupConversation(idsOfAll(elmer, meeper, bugs), id = RConvId("e"), name = Some("this name clearly matches"))
    val nameDoesntMatchButMemberGivenNameMatches = addGroupConversation(idsOfAll(elmer, carrot, bugs), id = RConvId("f"), name = Some("some other inconspicuous name"))
    val nameDoesntMatchButMemberSurnameMatches = addGroupConversation(idsOfAll(meeper, coyote, bugs), id = RConvId("g"), name = Some("a totally unrelated name"))
    val nameAndMembersMatch = addGroupConversation(idsOfAll(coyote, carrot, meeper), id = RConvId("h"), name = Some("checkmate chess"))
  }

  lazy val selfUser = api.getSelf
  lazy val convs = api.getConversations

  override protected def beforeAll(): Unit = {
    (Seq(self, meeper, coyote, elmer, bugs, carrot) ++ recommended) foreach (u => users += u.id -> u)
    Seq(meeper, coyote, elmer, bugs, carrot) foreach (u => addConnection(u.id))

    prepareContacts(providedContacts:_*)
    postAddressBookResponse = Seq(
      (recommended(0).id, Set(cid("I"))),
      (recommended(1).id, Set(cid("II"))),
      (coyote.id, Set.empty[ContactId]),
      (elmer.id, Set.empty[ContactId]))

    generated
    named

    super.beforeAll()
  }

  feature("Search") {
    scenario("Searching users by name.") {
      givenSomeUsersAndConversations()

      whenSearchingForUsers("röäd ") {
        _.getAll.userInfo should contain theSameElementsAs Seq(meeper)
      }
    }

    scenario("Searching group conversations by name.") {
      givenSomeUsersAndConversations()

      whenSearchingForGroups("Ç") { search =>
        idsOfAll(search.getAll:_*) should contain theSameElementsAs idsOfAll(
          generated.coyoteAndElmer,
          generated.bugsAndCarrot,
          named.nameMatchesButMembersDont,
          named.nameDoesntMatchButMemberGivenNameMatches,
          named.nameDoesntMatchButMemberSurnameMatches,
          named.nameAndMembersMatch
        )

        search.getAll should equal(search.getAll.sortBy(_.getName)(currentLocaleOrdering))
      }
    }

    scenario("Searching for oneself.") {
      givenSomeUsersAndConversations()

      whenSearchingForUsers("Cartoon") { search =>
        search.getAll shouldBe empty
      }
    }
  }

  feature("Top people") {

    scenario("Exclude just blocked users") {
      awaitUi(3.seconds) // previous tests spill into this one
      zmessaging.searchQueryCache.deleteBefore(now + 1.day).await()
      searchResults.put(TopPeople, Seq(meeper, coyote, elmer, bugs, carrot).map(_.entry()))
      val topPeople = api.search.getTopPeople(12, Array.empty)
      soon {
        idsOf(topPeople) shouldEqual idsOfAll(meeper, coyote, elmer, bugs, carrot)
      }
      val all = topPeople.getAll
      all should have size 5

      all(1).block() // block user
      soon {
        idsOf(topPeople) shouldEqual idsOfAll(meeper, elmer, bugs, carrot)
        topPeople.getAll should have size 4
      }
    }

    scenario("Exclude blocked users on new search") {
      val topPeople = api.search.getTopPeople(12, Array.empty)
      soon {
        idsOf(topPeople) shouldEqual idsOfAll(meeper, elmer, bugs, carrot)
      }
    }
  }

  feature("Connections") {

    scenario("Find all connections locally") {
      // meeper, elmer, bugs, carrot (cartoon is self, coyote is blocked (by previous test))
      val conns = api.search.getConnectionsByName("", 30, Array.empty)
      soon {
        conns should have size 4
        val all = conns.getAll
        all map (_.getName) shouldEqual Array("Bugs Bunny", "Carrot", "Elmer Fudd", "Road Runner")
      }
    }

    scenario("Find local connections by name") {
      val conns = api.search.getConnectionsByName("e", 30, Array.empty)
      soon(idsOf(conns) shouldEqual idsOfAll(elmer))

      val conns2 = api.search.getConnectionsByName("huntsman@thewoods.geo", 30, Array.empty)
      forAsLongAs(500.millis)(idsOf(conns2) shouldBe empty)

      val conns3 = api.search.getConnectionsByName("coyote", 30, Array.empty)
      forAsLongAs(500.millis)(idsOf(conns3) shouldBe empty)
    }

    scenario("Find local connections by name or email") {
      val conns = api.search.getConnectionsByNameOrEmailIncludingBlocked("el", 30, Array.empty)
      soon(idsOf(conns) shouldEqual idsOfAll(elmer))

      val conns2 = api.search.getConnectionsByNameOrEmailIncludingBlocked("hunt", 30, Array.empty)
      forAsLongAs(500.millis)(idsOf(conns2) shouldBe empty)

      val conns3 = api.search.getConnectionsByNameOrEmailIncludingBlocked("huntsman@thewoods.geo", 30, Array.empty)
      soon(idsOf(conns3) shouldEqual idsOfAll(elmer))

      val conns4 = api.search.getConnectionsByNameOrEmailIncludingBlocked("coyote", 30, Array.empty)
      soon(idsOf(conns4) shouldEqual idsOfAll(coyote))
    }


    scenario("Find filtered local connections") {
      val conns = api.search.getConnectionsByName("", 30, Array(meeper.id.str, bugs.id.str))
      soon(idsOf(conns) shouldEqual idsOfAll(carrot, elmer))
    }
  }

  private def givenSomeUsersAndConversations(): Unit = withDelay {
    selfUser.isLoggedIn shouldBe true
    convs should have size conversations.size - 1
    val uiUser: Set[User] = convs.flatMap(_.getUsers)(breakOut)
    forEvery (uiUser) { u => u.getName shouldEqual users(UserId(u.getId)).name.get }
  }

  private def whenSearchingForUsers(pattern: String)(f: UserSearchResult => Unit): Unit = {
    val search = api.search.getConnectionsByName(pattern, 30, Array.empty)
    val listener = returning(UpdateSpy())(search.addUpdateListener)
    withDelay {
      f(search)
    }
  }

  private def whenSearchingForGroups(pattern: String)(f: ConversationSearchResult => Unit): Unit = {
    val search = api.search.getGroupConversations(pattern, 12)
    val listener = returning(UpdateSpy())(search.addUpdateListener)
    withDelay {
      listener.numberOfTimesCalled should be >= 1
      f(search)
    }
  }

  override def postAddressBook(a: AddressBook): ErrorOrResponse[Seq[UserAndContactIds]] = CancellableFuture.delayed(1.second)(Right(postAddressBookResponse))(Threading.Background)

  @volatile var postAddressBookResponse: Seq[UserAndContactIds] = Nil

  lazy val providedContacts = Seq(
    Contact(ContactId("I"), "Bugs Hase", NameSource.StructuredName, "Bugs Hase", SearchKey("Bugs Hase"), Set(), Set(EmailAddress("bugs@bun.ny"))),
    Contact(ContactId("II"), "Karotte", NameSource.Nickname, "Karotte", SearchKey("Karotte"), Set(), Set(EmailAddress("c@arr.ot"))),
    Contact(ContactId("III"), "Valley", NameSource.Nickname, "Valley", SearchKey("Valley"), Set(), Set(EmailAddress("v@all.ey"))))

  def uids(strs: String*) = strs map UserId
  def cid(str: String) = ContactId(sha2(str))

  implicit class EnrichedUserInfo(user: UserInfo) { def entry(rel: Relation = Relation.First) = UserSearchEntry(user.id, user.name.get, user.accentId, Handle(user.name.get)) }
  implicit class EnrichedUserArray(users: Array[User]) { def userInfo: Seq[UserInfo] = users.map(u => UserInfo(UserId(u.getId), Some(u.getName)))(breakOut) }
}
