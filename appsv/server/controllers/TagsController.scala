/**
 * Copyright (c) 2016 Kaj Magnus Lindberg
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package controllers

import com.debiki.core._
import com.debiki.core.Prelude.IfBadAbortReq
import debiki.{JsonMaker, RateLimits, SiteTpi}
import debiki.EdHttp._
import ed.server.{EdContext, EdController}
import play.api.libs.json._
import javax.inject.Inject
import play.api.mvc.{Action, ControllerComponents}
import talkyard.server.JsX


class TagsController @Inject()(cc: ControllerComponents, edContext: EdContext)
  extends EdController(cc, edContext) {

  import context.globals

  def redirect: Action[Unit] = GetAction { apiReq =>
    Redirect(routes.TagsController.tagsApp("").url)
  }


  // For now, admin only?
  def tagsApp(clientRoute: String): Action[Unit] = AdminGetAction { apiReq =>
    _root_.controllers.dieIfAssetsMissingIfDevTest()
    val siteTpi = SiteTpi(apiReq)
    CSP_MISSING
    val pageBody = views.html.adminPage(siteTpi, appId = "theTagsApp").body
    Ok(pageBody) as HTML
  }


  def createTagType: Action[JsValue] = AdminPostJsonAction(
        maxBytes = 2000) { req =>  // RateLimits.CreateTag,
    import req.{dao, theRequester}
    val tagTypeMaybeId: TagType =
          JsX.parseTagType(req.body, Some(theRequester.id))(IfBadAbortReq)
    throwForbiddenIf(tagTypeMaybeId.id != NoTagTypeId, "TyE603MWEJ5",
          "Specify tag type id 0")
    debiki.dao.TagsDao.findTagLabelProblem(tagTypeMaybeId.dispName) foreach { errMsg =>
      throwBadRequest(errMsg.code, errMsg.message)
    }
    val tagType = dao.writeTx { (tx, _) => {
      val nextId = tx.nextTagTypeId()
      val tagType = tagTypeMaybeId.copy(id = nextId)(IfBadAbortReq)
      tx.upsertTagType(tagType)
      tagType
    }}
    OkSafeJson(Json.obj(
      "newTagType" -> JsX.JsTagType(tagType)))
  }


  def listTagTypes(forWhat: i32, tagNamePrefix: Opt[St]): Action[U] = GetAction { req =>
    val tagTypes = req.dao.getTagTypesSeq(forWhat, tagNamePrefix getOrElse "")
    OkSafeJson(JsArray(tagTypes map JsX.JsTagType))
  }


  @deprecated
  def loadAllTags: Action[Unit] = GetAction { request =>
    val tags = request.dao.loadAllTagsAsSet()
    OkSafeJson(JsArray(tags.toSeq.map(JsString)))
  }


  def loadTagsAndStats: Action[Unit] = GetAction { request =>
    import request.dao
    val (tagTypes, tagStats) = dao.readTx { tx =>
      (tx.loadAllTagTypes(),
          tx.loadTagTypeStats())
    }

    /*
    val tagsAndStats = request.dao.loadTagsAndStats()
    val isStaff = request.isStaff
     */
    val oldJson = JsonMaker.makeStorePatch(Json.obj(
      "allTagTypes" -> JsArray(tagTypes map JsX.JsTagType),
      "allTagTypeStatsById" ->
            JsObject(tagStats.map(s => s.toString -> JsX.JsTagStats(s))),
      /* Old!:
      "tagsStuff" -> Json.obj( "tagsAndStats" -> JsArray(tagsAndStats.map(tagAndStats => {
        Json.obj(
          "label" -> tagAndStats.label,
          "numTotal" -> tagAndStats.numTotal,
          "numPages" -> tagAndStats.numPages,
          // Don't think everyone should know about this:
          "numSubscribers" -> (if (isStaff) tagAndStats.numSubscribers else JsNull),
          "numMuted" -> (if (isStaff) tagAndStats.numMuted else JsNull))
       })))*/
      ), globals.applicationVersion)

      OkSafeJson(oldJson)
  }


  @deprecated
  def loadMyTagNotfLevels: Action[Unit] = GetAction { request =>
    val notfLevelsByTagLabel = request.dao.loadTagNotfLevels(request.theUserId, request.who)
    OkSafeJson(JsonMaker.makeStorePatch(
      // Old json!
      Json.obj("tagsStuff" -> Json.obj(
      "myTagNotfLevels" -> JsObject(notfLevelsByTagLabel.toSeq.map({ labelAndLevel =>
        labelAndLevel._1 -> JsNumber(labelAndLevel._2.toInt)
      })))), globals.applicationVersion))
  }


  @deprecated
  def setTagNotfLevel: Action[JsValue] = PostJsonAction(
          RateLimits.ConfigUser, maxBytes = 500) { request =>
    val body = request.body
    val tagLabel = (body \ "tagLabel").as[String]
    val notfLevelInt = (body \ "notfLevel").as[Int]
    val notfLevel = NotfLevel.fromInt(notfLevelInt) getOrElse throwBadRequest(
      "EsE40GK2W4", s"Bad tag notf level: $notfLevelInt")
    request.dao.setTagNotfLevelIfAuth(userId = request.theRoleId, tagLabel, notfLevel,
      request.who)
    Ok
  }


  def addRemoveTags: Action[JsValue] = PostJsonAction(
          RateLimits.EditPost, maxBytes = 5000) { req =>
    import req.{body, dao}
    val toAddJsVals: Seq[JsValue] = debiki.JsonUtils.parseJsArray(body, "tagsToAdd")
    val toAdd = toAddJsVals.map(v => JsX.parseTag(v, IfBadAbortReq))
    val toRemoveJsVals = debiki.JsonUtils.parseJsArray(body, "tagsToRemove")
    val toRemove = toRemoveJsVals.map(v => JsX.parseTag(v, IfBadAbortReq))
    val affectedPostIds = dao.addRemoveTagsIfAuth(
          toAdd = toAdd, toRemove = toRemove, req.who)

    val storePatch = dao.jsonMaker.makeStorePatchForPosts(
          postIds = affectedPostIds, showHidden = true, dao)

    OkSafeJson(storePatch)

    /*
    val pageId = (request.body \ "pageId").as[PageId]
    val postId = (request.body \ "postId").as[PostId]  // yes id not nr
    val tags = (request.body \ "tags").as[Set[TagLabel]]
    val patch = request.dao.addRemoveTagsIfAuth(pageId, postId, tags, request.who)
    OkSafeJson(patch) // or skip? or somehow include tags *only*? [5GKU0234]
     */
  }
}

