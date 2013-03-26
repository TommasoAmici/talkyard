// Copyright (c) 2012 Kaj Magnus Lindberg (born 1979)

package com.debiki.v0

import java.{util => ju}
import collection.{immutable => imm, mutable => mut}
import Prelude._
import PageParts._
import FlagReason.FlagReason
import com.debiki.v0.{PostActionPayload => PAP}


object PostActionOld {

  // Remove, when all PostActionDtoOld have been replaced with PostActionDto.
  def apply(page: PageParts, action: PostActionDtoOld): PostActionOld = action match {
    case a: PostActionDto[_] => page.getActionById(action.id) getOrDie "DwE20KF58"
    case a: PostActionDtoOld => new PostActionOld(page, a)
  }

}



class PostAction[P](  // [P <: PostActionPayload] causes compilation errors
  page: PageParts,
  val actionDto: PostActionDto[P]) extends PostActionOld(page, actionDto) {

  def postId = actionDto.postId
  def payload: P = actionDto.payload
}



trait MaybeApproval {

  /** If defined, this action implicitly approves the related post.
    *
    * For example, if an admin edits a post, then `edit.approval`
    * might be set to Approval.AuthoritativeUser, and `edit.isApplied`
    * would be set to true, and then the new version of the edited post
    * has "automatically" been approved.
    */
  def approval: Option[Approval]

}



// SmartPageAction[Rating].
/** A virtual Action, that is, an Action plus some utility methods that
 *  look up other stuff in the relevant Debate.
 */
class PostActionOld(val debate: PageParts, val action: PostActionDtoOld) {
  def page = debate // should rename `debate` to `page`
  def id: String = action.id
  def creationDati = action.ctime
  def loginId = action.loginId
  def login: Option[Login] = debate.people.login(action.loginId)
  def login_! : Login = login.getOrElse(runErr(
     "DwE6gG32", "No login with id "+ safed(action.loginId) +
     " for action "+ safed(id)))
  def identity: Option[Identity] = login.flatMap(l =>
                                    debate.people.identity(l.identityId))
  def identity_! : Identity = debate.people.identity_!(login_!.identityId)
  def userId = {
    // Temporary (?) debug test: (I just introduced `userId`)
    user foreach { u => assErrIf(u.id != action.userId, "DwE43KbX6") }
    action.userId
  }
  def user : Option[User] = identity.flatMap(i => debate.people.user(i.userId))
  def user_! : User = debate.people.user_!(identity_!.userId)
  def ip: Option[String] = action.newIp.orElse(login.map(_.ip))
  def ip_! : String = action.newIp.getOrElse(login_!.ip)
  def ipSaltHash: Option[String] = ip.map(saltAndHashIp(_))
  def ipSaltHash_! : String = saltAndHashIp(ip_!)

  def isTreeDeleted = {
    // In case there are > 1 deletions, consider the first one only.
    // (Once an action has been deleted, it isn't really possible to
    // delete it again in some other manner? However non transactional
    // (nosql) databases might return many deletions? and we should
    // care only about the first.)
    firstDelete.map(_.wholeTree) == Some(true)
  }

  def isDeleted = deletions nonEmpty

  // COULD optimize this, do once for all posts, store in map.
  lazy val deletions = debate.deletions.filter(_.postId == action.id)

  /** Deletions, the most recent first. */
  lazy val deletionsDescTime = deletions.sortBy(- _.ctime.getTime)

  lazy val lastDelete = deletionsDescTime.headOption
  lazy val firstDelete = deletionsDescTime.lastOption

  def deletionDati: Option[ju.Date] = firstDelete.map(_.ctime)
}



/**
 * Takes into account all edits applied to the actual post.
 *
 * Created via CreatePostAction:s.
 */
class Post(debate: PageParts, theActionDto: PostActionDto[PostActionPayload.CreatePost])
  extends PostAction[PostActionPayload.CreatePost](debate, theActionDto)
  with MaybeApproval {

  def parentId: String = payload.parentPostId

  def parentPost: Option[Post] =
    if (parentId == id) None
    else debate.getPost(parentId)


  def approval = payload.approval


  def isArticleOrConfig =
    id == PageParts.TitleId || id == PageParts.BodyId || id == PageParts.ConfigPostId


  def initiallyApproved: Boolean = {
    // If initially preliminarily auto approved, that approval is cancelled by
    // any contiguous and subsequent rejection. (And `lastApproval` takes
    // that cancellation into account.)
    lastApproval.isDefined && approval.isDefined
  }


  lazy val (text: String, markup: String) = _applyEdits


  def actions: List[PostAction[_]] = page.getActionsByPostId(id)


  /**
   * Applies all edits and returns the resulting text and markup.
   *
   * Keep in sync with textAsOf in debiki.js.
   *    COULD make textAsOf understand changes in markup type,
   *    or the preview won't always work correctly.
   *
   * (It's not feasible to apply only edits that have been approved,
   * or only edits up to a certain dati. Because
   * then the state of all edits at that dati would have to be computed.
   * But at a given dati, an edit might or might not have been applied and
   * approved, and taking that into account results in terribly messy code.
   * Instead, use Page.splitByVersion(), to get a version of the page
   * at a certain point in time.)
   */
  private def _applyEdits: (String, String) = {
    var curText = payload.text
    var curMarkup = payload.markup
    val dmp = new name.fraser.neil.plaintext.diff_match_patch
    // Loop through all edits and patch curText.
    for (edit <- editsAppliedAscTime) {
      curMarkup = edit.newMarkup.getOrElse(curMarkup)
      val patchText = edit.patchText
      if (patchText nonEmpty) {
        // COULD check [1, 2, 3, …] to find out if the patch applied
        // cleanaly. (The result is in [0].)
        type P = name.fraser.neil.plaintext.diff_match_patch.Patch
        val patches: ju.List[P] = dmp.patch_fromText(patchText) // silly API, ...
        val p2 = patches.asInstanceOf[ju.LinkedList[P]] // returns List but needs...
        val result = dmp.patch_apply(p2, curText) // ...a LinkedList
        val newText = result(0).asInstanceOf[String]
        curText = newText
      }
    }
    (curText, curMarkup)
  }

  def textInitially: String = payload.text
  def where: Option[String] = payload.where
  def edits: List[Patch] = page.editsFor(id)

  lazy val (
      editsDeletedDescTime: List[Patch],
      editsPendingDescTime: List[Patch],
      editsAppliedDescTime: List[Patch],
      editsRevertedDescTime: List[Patch]) = {

    var deleted = List[Patch]()
    var pending = List[Patch]()
    var applied = List[Patch]()
    var reverted = List[Patch]()

    edits foreach { edit =>
      // (Cannot easily move the below `assert`s to Edit, because
      // the values they test are computed lazily.)

      // An edit cannot be both applied and reverted at the same time.
      assErrIf(edit.isApplied && edit.isReverted, "DwE4FW21")
      if (edit.isReverted) reverted ::= edit
      if (edit.isApplied) applied ::= edit

      // An edit can have been reverted and then deleted,
      // but an edit that is currently in effect cannot also be deleted.
      assErrIf(edit.isApplied && edit.isDeleted, "DwE09IJ3")
      if (edit.isDeleted) deleted ::= edit

      // An edit that has been applied and then reverted, is pending again.
      // But an edit that is in effect, or has been deleted, cannot be pending.
      assErrIf(edit.isApplied && edit.isPending, "DwE337Z2")
      assErrIf(edit.isDeleted && edit.isPending, "DwE8Z3B2")
      if (edit.isPending) pending ::= edit
    }

    (deleted.sortBy(- _.deletionDati.get.getTime),
       pending.sortBy(- _.creationDati.getTime),
       applied.sortBy(- _.applicationDati.get.getTime),
       reverted.sortBy(- _.revertionDati.get.getTime))
  }

  def editsAppliedAscTime = editsAppliedDescTime.reverse

  lazy val lastEditApplied = editsAppliedDescTime.headOption
  lazy val lastEditReverted = editsRevertedDescTime.headOption


  /**
   * Modification date time: When the text of this Post was last changed
   * (that is, an edit was applied, or a previously applied edit was reverted).
   */
  def modificationDati: ju.Date = {
    val maxTime = math.max(
       lastEditApplied.map(_.applicationDati.get.getTime).getOrElse(0: Long),
       lastEditReverted.map(_.revertionDati.get.getTime).getOrElse(0: Long))
    if (maxTime == 0) creationDati else new ju.Date(maxTime)
  }


  private lazy val _reviewsDescTime: List[PostActionOld with MaybeApproval] = {
    // (If a ReviewPostAction.approval.isEmpty, this Post was rejected.
    // An Edit.approval or EditApp.approval being empty, however,
    // means only that this Post has not yet been reviewed — so the Edit
    // or EditApp is simply ignored.)
    var explicitReviews = page.explicitReviewsOf(id).sortBy(-_.creationDati.getTime)
    var implicitApprovals = List[PostActionOld with MaybeApproval]()
    if (approval.isDefined)
      implicitApprovals ::= this
    for (edit <- edits) {
      if (edit.approval.isDefined)
        implicitApprovals ::= edit
      for (editApp <- page.editAppsByEdit(edit.id)) {
        // Ought to reuse PostActionsWrapper's wrapped actionDto:s rather than
        // creating new objects here.
        if (editApp.approval.isDefined)
          implicitApprovals ::= new ApplyPatchAction(page, editApp)
        for (editAppReview <- page.explicitReviewsOf(editApp)) {
          explicitReviews ::= editAppReview
        }

        // In the future, deletions (and any other actions?) should also
        // be considered:
        //for (deletion <- page.deletionsFor(editApp)) {
        //  if (deletion.approval.isDefined)
        //    implicitApprovals ::= deletion
        //  ... check explicit reviews of `deletion`.
        //}
      }

      // In the future, also consider deletions?
      //for (deletion <- page.deletionsFor(edit)) {
      //  if (deletion.approval.isDefined)
      //    implicitApprovals ::= deletion
      //  ... check explicit reviews of `deletion`.
      //}
    }

    // In the future, consider deletions of `this.action`?
    // for (deletion <- page.deletionsFor(action)) ...

    val allReviews = explicitReviews ::: implicitApprovals
    allReviews.sortBy(- _.creationDati.getTime)
  }


  /**
   * Moderators need only be informed about things that happened after
   * this dati.
   */
  def lastAuthoritativeReviewDati: Option[ju.Date] =
    lastAuthoritativeReview.map(_.creationDati)


  def lastReviewDati: Option[ju.Date] =
    _reviewsDescTime.headOption.map(_.creationDati)


  lazy val lastApproval: Option[PostActionOld with MaybeApproval] = {
    // A rejection cancels all earlier and contiguous preliminary auto
    // approvals, so loop through the reviews:
    var rejectionFound = false
    var reviewsLeft = _reviewsDescTime
    var lastApproval: Option[PostActionOld with MaybeApproval] = None
    while (reviewsLeft nonEmpty) {
      val review = reviewsLeft.head
      reviewsLeft = reviewsLeft.tail
      if (review.approval == Some(Approval.Preliminary) && rejectionFound) {
        // Ignore this approval — the rejection cancels it.
      } else if (review.approval.isEmpty) {
        rejectionFound = true
      } else {
        lastApproval = Some(review)
        reviewsLeft = Nil
      }
    }
    lastApproval
  }


  /**
   * The most recent review of this post by an admin or moderator.
   * (But not by any well behaved user, which the computer might have decided
   * to trust.)
   */
  def lastAuthoritativeReview: Option[PostActionOld with MaybeApproval] =
    _reviewsDescTime.find(review =>
       review.approval != Some(Approval.Preliminary) &&
       review.approval != Some(Approval.WellBehavedUser))


  def lastPermanentApproval: Option[PostActionOld with MaybeApproval] =
    _reviewsDescTime.find(review =>
        review.approval.isDefined &&
        review.approval != Some(Approval.Preliminary))


  /**
   * All actions that affected this Post and didn't happen after
   * this dati should be considered when rendering this Post.
   */
  def lastApprovalDati: Option[ju.Date] = lastApproval.map(_.creationDati)


  def lastManualApprovalDati: Option[ju.Date] =
    _reviewsDescTime.find(_.approval == Some(Approval.Manual)).map(_.creationDati)


  def lastReviewWasApproval: Option[Boolean] =
    if (lastReviewDati.isEmpty) None
    else Some(lastReviewDati == lastApprovalDati)


  def currentVersionReviewed: Boolean = {
    // Use >= not > because a comment might be auto approved, and then
    // the approval dati equals the comment creationDati.
    lastReviewDati.isDefined && lastReviewDati.get.getTime >=
       modificationDati.getTime
  }


  def currentVersionPrelApproved: Boolean =
    currentVersionReviewed &&
       lastApproval.flatMap(_.approval) == Some(Approval.Preliminary)


  def currentVersionApproved: Boolean =
    currentVersionReviewed && lastReviewWasApproval.get


  def currentVersionRejected: Boolean =
    currentVersionReviewed && !lastReviewWasApproval.get


  def someVersionApproved: Boolean = {
    // A rejection cancels all edits since the previous approval,
    // or effectively deletes the post, if it has never been approved.
    // To completely "unapprove" a post that has previously been approved,
    // delete it instead.
    lastApprovalDati.nonEmpty
  }


  def someVersionPermanentlyApproved: Boolean =
    lastPermanentApproval.nonEmpty


  def someVersionManuallyApproved: Boolean =
    lastManualApprovalDati.isDefined


  lazy val depth: Int = {
    var depth = 0
    var curId = id
    var nextParent = page.getPost(parentId)
    while (nextParent.nonEmpty && nextParent.get.id != curId) {
      depth += 1
      curId = nextParent.get.id
      nextParent = page.getPost(nextParent.get.parentId)
    }
    depth
  }


  def replyCount: Int =
    debate.repliesTo(id).length


  def replies: List[Post] = debate.repliesTo(id)


  def siblingsAndMe: List[Post] = debate.repliesTo(parentId)


  def isTreeCollapsed: Boolean =
    hasHappenedNotUndone(PostActionPayload.CollapseTree)


  def isOnlyPostCollapsed: Boolean =
    hasHappenedNotUndone(PostActionPayload.CollapsePost)


  def areRepliesCollapsed: Boolean =
    hasHappenedNotUndone(PostActionPayload.CollapseReplies)

  def isTreeClosed: Boolean =
    hasHappenedNotUndone(PostActionPayload.CloseTree)


  private def hasHappenedNotUndone(payload: PostActionPayload): Boolean =
    actions.find { action =>
      action.payload == payload  // && !action.wasUndone
    }.nonEmpty


  // COULD optimize this, do once for all flags.
  lazy val flags = debate.flags.filter(_.postId == this.id)


  def flagsDescTime: List[Flag] = flags.sortBy(- _.ctime.getTime)


  /**
   * Flags that were raised before the last review by a moderator have
   * already been reviewed.
   */
  lazy val (
      flagsPendingReview: List[Flag],
      flagsReviewed: List[Flag]) =
    flagsDescTime span { flag =>
      if (lastAuthoritativeReviewDati isEmpty) true
      else lastAuthoritativeReviewDati.get.getTime <= flag.ctime.getTime
    }


  lazy val lastFlag = flagsDescTime.headOption

  lazy val flagsByReason: imm.Map[FlagReason, List[Flag]] = {
    // Add reasons and flags to a mutable map.
    var mmap = mut.Map[FlagReason, mut.Set[Flag]]()
    for (f <- flags)
      mmap.getOrElse(f.reason, {
        val s = mut.Set[Flag]()
        mmap.put(f.reason, s)
        s
      }) += f
    // Copy to an immutable version.
    imm.Map[FlagReason, List[Flag]](
      (for ((reason, flags) <- mmap)
      yield (reason, flags.toList)).toList: _*)
  }

  /** Pairs of (FlagReason, flags-for-that-reason), sorted by
   *  number of flags, descending.
   */
  lazy val flagsByReasonSorted: List[(FlagReason, List[Flag])] = {
    flagsByReason.toList.sortWith((a, b) => a._2.length > b._2.length)
  }

}



class Patch(debate: PageParts, val edit: Edit)
  extends PostActionOld(debate, edit) with MaybeApproval {

  def post = debate.getPost(edit.postId)
  def post_! = debate.getPost_!(edit.postId)

  def patchText = edit.text
  def newMarkup = edit.newMarkup

  def isPending = !isApplied && !isDeleted

  def approval = edit.approval

  private def _initiallyAutoApplied = edit.autoApplied

  /**
   * The result of applying patchText to the related Post, as it was
   * when this Edit was submitted (with other later edits ignored, even
   * if they were actually applied before this edit).
   */
  def intendedResult: String =
    // Apply edits to the related post up to this.creationDati,
    // then apply this edit, and return the resulting text.
    "(not implemented)"

  /**
   * The result of applying patchText to the related Post,
   * taking into account other Edits that were applied before this edit,
   * and they made it impossible to correctly
   * apply the patchText of this Edit.
   */
  def actualResult: Option[String] =
    // Apply edits to the related post up to this.applicationDati,
    // then apply this edit, and return the resulting text.
    if (isApplied) Some("(not implemented)") else None


  // COULD let isApplied, applicationDati, applierLoginId, applicationActionId
  // be functions, and remember only Some(applicationAction)?
  val (isApplied, applicationDati,
      applierLoginId, applicationActionId,
      isReverted, revertionDati)
        : (Boolean, Option[ju.Date], Option[String], Option[String],
          Boolean, Option[ju.Date]) = {
    val allEditApps = page.editAppsByEdit(id)
    if (allEditApps isEmpty) {
      // This edit might have been auto applied, and deleted & reverted later.
      (_initiallyAutoApplied, isDeleted) match {
        case (false, _) => (false, None, None, None, false, None)
        case (true, false) =>
          // Auto applied at creation. This edit itself is its own application.
          (true, Some(creationDati), Some(loginId), Some(id), false, None)
        case (true, true) =>
          // Auto applied, but later deleted and implicitly reverted.
          (false, None, None, None, true, deletionDati)
      }
    } else {
      // Consider the most recent EditApp only. If it hasn't been deleted,
      // this Edit is in effect. However, if the EditApp has been deleted,
      // there should be no other EditApp that has not also been deleted,
      // because you cannot apply an edit that's already been applied.
      val lastEditApp = allEditApps.maxBy(_.ctime.getTime)
      val revertionDati =
        page.deletionFor(lastEditApp.id).map(_.ctime) orElse deletionDati
      if (revertionDati isEmpty)
        (true, Some(lastEditApp.ctime),
           Some(lastEditApp.loginId), Some(lastEditApp.id), false, None)
      else
        (false, None, None, None, true, Some(revertionDati.get))
    }
  }

}



class ApplyPatchAction(page: PageParts, val editApp: EditApp)
  extends PostActionOld(page, editApp) with MaybeApproval {
  def approval = editApp.approval
}


class Review(page: PageParts, val review: ReviewPostAction)
  extends PostActionOld(page, review) with MaybeApproval {

  def approval = review.approval
  lazy val target: Post = page.getPost(review.postId) getOrDie "DwE93UX7"

}


// vim: fdm=marker et ts=2 sw=2 tw=80 fo=tcqwn list

