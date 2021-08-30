package com.debiki.core

import Prelude._

case class TagType(
  id: TagTypeId,
  canTagWhat: i32,
  urlSlug_unimpl: Opt[St], // later
  dispName: St,
  createdById: PatId,
)(ifBad: DieOrComplain) {
  import ifBad.require
  require(id >= 0, "TyE4MR507")
  require(dispName.isTrimmedNonEmpty, "TyE06MWEP3")
}


case class Tag(
  id: TagId,
  tagTypeId: TagTypeId,
  parentTagId_unimpl: Opt[TagId], //  later
  onPatId: Opt[PatId],
  onPostId: Opt[PostId],
)(ifBad: DieOrComplain) {
  import ifBad.require
  require(id >= 0, "TyE5GMRA25")
  require(tagTypeId >= 0, "TyE5GMRA26")
  require(onPatId.isDefined != onPostId.isDefined, "TyE2J3MRD2")
  require(onPostId.forall(_ > 0), "TyE9J370S7")
}


object Tag {

  /*
  def create(
    ifBad: DieOrComplain,
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
