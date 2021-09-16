package com.debiki.core

import Prelude._

case class TagType(
  id: TagTypeId,
  canTagWhat: i32,
  urlSlug_unimpl: Opt[St], // later
  dispName: St,
  createdById: PatId,
)(aborter: MessAborter) {
  import aborter.require
  require(id >= 0, "TyE4MR507")
  require(dispName.isTrimmedNonEmpty, "TyE06MWEP3")
  require(canTagWhat == 7 || canTagWhat == 56, "TyE4062MW5", "canTagWhat must be 7 or 56")
}


case class Tag(
  id: TagId,
  tagTypeId: TagTypeId,
  parentTagId_unimpl: Opt[TagId], //  later
  onPatId: Opt[PatId],
  onPostId: Opt[PostId],
)(aborter: MessAborter) {
  import aborter.require
  require(id >= 0, "TyE5GMRA25")
  require(tagTypeId >= 0, "TyE5GMRA26")
  require(onPatId.isDefined != onPostId.isDefined, "TyE2J3MRD2")
  require(onPostId.forall(_ > 0), "TyE9J370S7")
}


object Tag {

  /*
  def create(
    ifBad: Aborter,
    id: TagId,
    tagTypeId: TagTypeId,
    parentTagId_unimpl: Opt[TagId], //  later
    onPatId: Opt[PatId],
    onPostId: Opt[PostId],
  ): Tag = {

    maxUploadBytes foreach { maxBytes =>   // [server_limits]
      dieOrComplainIf(maxBytes < 0, s"Max bytes negative: $maxBytes [TyE3056RMD24]",
        ifBad)
    }

    PatPerms(maxUploadBytes = maxUploadBytes,
      allowedUplExts = allowedUplExts.noneIfBlank,
    )(usingPatPermsCreate = true)
  } */

}
