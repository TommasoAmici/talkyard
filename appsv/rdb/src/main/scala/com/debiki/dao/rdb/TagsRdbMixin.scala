/**
 * Copyright (c) 2021 Kaj Magnus Lindberg
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

package com.debiki.dao.rdb

import com.debiki.core._
import com.debiki.core.Prelude._
import Rdb._
import RdbUtil._
import java.sql.{ResultSet => j_ResultSet}
import collection.{mutable => mut}



/** Manages tags on posts and pats.
  */
trait TagsRdbMixin extends SiteTx {
  self: RdbSiteTransaction =>


  def nextTagTypeId(): i32 = {
    runQueryFindNextFreeInt32(tableName = "tagtypes_t", columnName = "id_c")
    /*
    val query = """
          select max(id_c) as max_id from tagtypes_t where site_id_c = ?  """
    val curMax = runQueryFindExactlyOne(
          query, List(siteId.asAnyRef), rs => getOptInt32(rs, "max_id"))
    // Let's start at 1001 so there's room for some built-in tag types,
    // e.g. My Notes or Staff Notes, or bookmarks.
    (curMax getOrElse 1000) + 1
     */
  }


  def loadAllTagTypes(): Seq[TagType] = {
    val query = """
          select * from tagtypes_t where site_id_c = ? """
    runQueryFindMany(query, List(siteId.asAnyRef), parseTagType)
  }


  def loadTagTypeStats(): Seq[TagTypeStats] = {
    val query = """
          select
            tagtype_id_c,
            count(*) num_total,
            sum(one_unless_null(on_post_id_c)) num_post_tags,
            sum(one_unless_null(on_pat_id_c))  num_pat_badges
          from tags_t
          where site_id_c = ?
          group by tagtype_id_c
          """
    runQueryFindMany(query, List(siteId.asAnyRef), rs => {
      TagTypeStats(
            tagTypeId = getInt32(rs, "tagtype_id_c"),
            numTotal = getInt32(rs, "num_total"),
            // How can any of these be null, in spite of one_unless_null() above,
            // which always returns 0 or 1? Anyway, let's just use getOpt... getOrElse 0.
            numPostTags = getOptInt32(rs, "num_post_tags") getOrElse 0,
            numPatBadges = getOptInt32(rs, "num_pat_badges") getOrElse 0)
    })
  }


  def upsertTagType(tagType: TagType): U = {
    require(tagType.id >= 1, s"Bad tagtype id: ${tagType.id} [TyE03MFP64]")
    val statement = s"""
          insert into tagtypes_t (
              site_id_c,
              id_c,
              can_tag_what_c,
              url_slug_c,
              disp_name_c,
              created_by_id_c)
          values (?, ?, ?, ?, ?, ?)
          on conflict (site_id_c, id_c) do update set
              can_tag_what_c = excluded.can_tag_what_c,
              url_slug_c = excluded.url_slug_c,
              disp_name_c = excluded.disp_name_c
              -- leave created_by_id_c as is
              """
    val values = List(
          siteId.asAnyRef,
          tagType.id.asAnyRef,
          tagType.canTagWhat.asAnyRef,
          NullVarchar, // later: tagType.urlSlug_unimpl,
          tagType.dispName,
          tagType.createdById.asAnyRef)
    runUpdateExactlyOneRow(statement, values)
  }


  def hardDeleteTagType(tagType: TagType): Bo = {
    val statement = s"""
          delete from tagtypes_t where site_id_c = ? and id_c = ? """
    val values = List(siteId.asAnyRef, tagType.id.asAnyRef)
    runUpdateSingleRow(statement, values)
  }


  def loadTagsByPatId(patId: PatId): Seq[Tag] = {
    val query = """
          select * from tags_t where site_id_c = ? and on_pat_id_c = ? """
    runQueryFindMany(query, List(siteId.asAnyRef, patId.asAnyRef), parseTag)
  }
NEXT !!!@@@###
  def loadTagsForPages(pageIds: Iterable[PageId]): Map[PageId, ImmSeq[Tag]] = {
    val values = siteId.asAnyRef :: pageIds.toList
    val query = s"""
          select po.page_id, t.* from tags_t t
              inner join posts3 po
                  on po.site_id = t.site_id_c
                  and po.site_id = ?  -- this and...
                  and po.page_id in (${ makeInListFor(pageIds) })
                  and po.unique_post_id = t.on_post_id_c
                  and po.nr = $BodyNr
          -- where t.site_id = ?  -- this should be equivalent
          """
    runQueryBuildMultiMap(query, values, rs => {
      val pageId = getString(rs, "page_id")
      val tag = parseTag(rs)
      pageId -> tag
    })
  }

  def loadTagsToRenderSmallPage(pageId: PageId): Seq[Tag] = {
    // Small page, we can load all tags. [large_pages]
    // But if page large, say, 10 000+ posts, then maybe not.
    // Load tags on posts on the page, and also tags (user badges) on post authors.
    val query = """
          select t.* from tags_t t
              inner join posts3 po
                  on po.site_id = t.site_id_c
                  and po.page_id = ?
                  and po.site_id = ?  -- this and...
                  and (po.unique_post_id = t.on_post_id_c  or
                       po.created_by_id  = t.on_pat_id_c)
          -- where t.site_id = ?  -- this should be equivalent
          """
    runQueryFindMany(query, List(pageId.asAnyRef, siteId.asAnyRef), parseTag)
  }


  override def loadPostTagsAndAuthorBadges(postIds: Iterable[PostId]): TagsAndBadges = {
    if (postIds.isEmpty)
      return TagsAndBadges(
            Map.empty.withDefaultValue(Nil),
            Map.empty.withDefaultValue(Nil))

    val query = s"""
          -- Post tags
          select * from tags_t
          where site_id_c = ?
            and on_post_id_c in (${ makeInListFor(postIds) })
          union
          -- Post author user badges
          select t.*
          from tags_t t inner join posts3 po
            on t.site_id_c = po.site_id
            and t.on_pat_id_c = po.created_by_id
            and po.site_id = ?
            and po.unique_post_id in (${ makeInListFor(postIds) })
          -- order by on_post_id_c
          -- -- Place post tags first, with on_pat_id_c = -1,
          -- -- then all user badges, with on_post_id_c = -1.
          -- order by
          --   coalesce(on_pat_id_c, -1),
          --   coalesce(on_post_id_c, -1)
          """
    var values = siteId.asAnyRef :: postIds.toList.map(_.asAnyRef)
    values = values:::values

    val tags = MutArrBuf[Tag]()
    //var curPostId = NoPostId
    var curPatId = NoUserId
    val tagsByPostId = mut.Map[PostId, MutArrBuf[Tag]]()
    val tagsByPatId = mut.Map[PatId, MutArrBuf[Tag]]()


    runQueryAndForEachRow(query, values, rs => {
      val tag = parseTag(rs)
      if (tag.onPostId.isDefined) {
        val postId: PostId = tag.onPostId getOrDie "TyE4MFE6780"
        val anyTags: Opt[MutArrBuf[Tag]] = tagsByPostId.get(postId)
        val tags = anyTags getOrElse MutArrBuf[Tag]()
        tags.append(tag)
        if (anyTags.isEmpty) {
          tagsByPostId(postId) = tags
        }
      }
      else if (tag.onPatId.isDefined) {
        val patId = tag.onPatId getOrDie "TyEJ503MRE"
        val anyTags: Opt[MutArrBuf[Tag]] = tagsByPatId.get(patId)
        val tags = anyTags getOrElse MutArrBuf[Tag]()
        tags.append(tag)
        if (anyTags.isEmpty) {
          tagsByPatId(patId) = tags
        }
      }
      else {
        die("TyE0WME573")
      }

      /*
      if (tag.onPostId.isDefined) {
        val postId = tag.onPostId.get
        if (curPostId == NoPostId || curPostId == postId) {
          curPostId = postId
          tags += tag
        }
        else {
          tagsByPostId = tagsByPostId.updated(curPostId, tags.toVector)
          tags.clear()
          tags += tag
          curPostId = postId
        }
      }
      else {
        val patId = tag.onPatId getOrDie "TyEJ503MRE"
        if (curPatId == NoUserId || curPatId == patId) {
          curPatId = patId
          tags += tag
        }
        else {
          tagsByPatId = tagsByPatId.updated(curPatId, tags.toVector)
          tags.clear()
          tags += tag
          curPatId = patId
        }
      }*/
    })
    /*
    if (curPostId != NoPostId) {
      tagsByPostId = tagsByPostId.updated(curPostId, tags.toVector)
    }*/
    TagsAndBadges(
          tags = tagsByPostId.withDefaultValue(MutArrBuf[Tag]()),
          badges = tagsByPatId.withDefaultValue(MutArrBuf[Tag]()))
  }


  def nextTagId(): i32 = {
    runQueryFindNextFreeInt32(tableName = "tags_t", columnName = "id_c")
  }


  def addTag(tag: Tag): U = {
    val statement = s"""
          insert into tags_t (
              site_id_c,
              id_c,
              tagtype_id_c,
              parent_tag_id_c,
              on_pat_id_c,
              on_post_id_c)
            values (?, ?, ?, ?, ?, ?) """
    val values = List(
          siteId.asAnyRef,
          tag.id.asAnyRef,
          tag.tagTypeId.asAnyRef,
          tag.parentTagId_unimpl.orNullInt,
          tag.onPatId.orNullInt,
          tag.onPostId.orNullInt)
    runUpdateExactlyOneRow(statement, values)
  }


  def removeTags(tags: Seq[Tag]): U = {
    if (tags.isEmpty) return ()
    val statement = s"""
          delete from tags_t where site_id_c = ? and id_c in (${makeInListFor(tags)}) """
    val values = siteId.asAnyRef :: tags.map(_.id.asAnyRef).toList
    runUpdate(statement, values)
  }


  private def parseTagType(rs: j_ResultSet): TagType = {
    TagType(
          id = getInt(rs, "id_c"),
          canTagWhat = getInt(rs, "can_tag_what_c"),
          urlSlug_unimpl = getOptString(rs, "url_slug_c"),
          dispName = getString(rs, "disp_name_c"),
          createdById = getInt(rs, "created_by_id_c"))(IfBadDie)
  }

  private def parseTag(rs: j_ResultSet): Tag = {
    Tag(id = getInt(rs, "id_c"),
          tagTypeId = getInt(rs, "tagtype_id_c"),
          parentTagId_unimpl = getOptInt32(rs, "parent_tag_id_c"),
          onPatId = getOptInt32(rs, "on_pat_id_c"),
          onPostId = getOptInt32(rs, "on_post_id_c"))(IfBadDie)
  }

}
