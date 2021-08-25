package talkyard.server.api

import com.debiki.core._
import com.debiki.core.Prelude._
import debiki.dao.SiteDao
import debiki.EdHttp._
import play.api.libs.json.{JsObject, JsValue, JsArray, Json}
import org.scalactic.{Bad, ErrorMessage, Good, Or}
import debiki.JsonUtils._
import collection.{mutable => mut}

case class ActionDoer(dao: SiteDao, reqerId: ReqrId) { // later, tags branch:  complain: DieOrComplain) {

  def doAction(action: ApiAction): AnyProblem = {
    action.doHow match {
      case params: SetVoteParams =>
        // Currently only for setting num Like votes to exactly 1.
        dieIf(action.doWhat != ActionType.SetVote, "TyEBADACTYP1")

        throwUnimplIf(params.whatVote != PostVoteType.Like,
              "TyE062MSE: Can only Like vote via the API, currently.")
        val page = getThePageByRef(params.whatPage)
        if (params.howMany == 1) {
          dao.addVoteIfAuZ(page.pageId, postNr = params.whatPostNr,
                voteType = params.whatVote,
                voterId = action.asWho.id, voterIp = "SKIP_IP", postNrsRead = Set.empty)
        }
        else if (params.howMany == 0) {
          dao.deleteVoteIfAuZ(page.pageId, postNr = params.whatPostNr,
                voteType = params.whatVote,
                voterId = action.asWho.id)
        }
        else {
          die("TyE4MWEGJ6702")
        }
        /*
        dao.writeTx { (tx, _) =>
          // For now, can only Like vote.
          tx.insertPostAction(PostVote(  // [exp] ok to use
                uniqueId: PostId,   // RENAME to postId
                pageId = page.pageId,
                postNr = params.wha
                doneAt: When,
                voterId: UserId,
                voteType: PostVoteType))
        } */
      case params: SetNotfLevelParams =>
        // Currently only for setting the notf level to EveryPost.
        dieIf(action.doWhat != ActionType.SetNotfLevel, "TyEBADACTYP2")
        val pageMeta = getAnyPageByRef(params.whatPage)
        throwUnimplIf(pageMeta.isEmpty,
              "TyE5MF72: Can only change page notf prefs via API, currently")
        val newNotfPref = PageNotfPref(
              peopleId = action.asWho.id,
              notfLevel = params.whatLevel,
              pageId = pageMeta.map(_.pageId))
        dao.savePageNotfPref(newNotfPref, reqerId)
        //dao.writeTx { (tx, _) =>
        //  tx.upsertPageNotfPref(newNotfPref)
        //}
    }
    Fine
  }


  private val pageIdsByRef = mut.Map[PageRef, Opt[PageMeta]]()

  private def getThePageByRef(ref: PageRef): PageMeta = {
    getAnyPageByRef(ref) getOrElse throwNotFound("TyE502MRGP4", s"No such page: $ref")
  }

  private def getAnyPageByRef(ref: PageRef): Opt[PageMeta] = {
    pageIdsByRef.getOrElseUpdate(
          ref, dao.getPageMetaByParsedRef(ref.asParsedRef))
  }

}
