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



/** Manages tags on posts and pats.
  */
trait TagsRdbMixin extends SiteTx {
  self: RdbSiteTransaction =>


  def loadAllTagTypes(): Seq[TagType] = {
    val query = """
          select * from tagtypes_t where site_id = ? """
    runQueryFindMany(query, List(siteId.asAnyRef), parseTagType)
  }


  def upsertTagType(tagType: TagType): U = {
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
          select * from tags_t where site_id = ? and on_pat_id_c = ? """
    runQueryFindMany(query, List(siteId.asAnyRef, patId.asAnyRef), parseTag)
  }


  def loadTagsToRenderPage(pageId: PageId): Seq[Tag] = {
    // Load tags on posts on the page, and also tags (user badges) on post authors.
    val query = """
          select t.* from tags_t t
              inner join posts3 po
                  on po.site_id = t.site_id
                  and po.page_id = ?
                  and po.site_id = ?  -- this and...
                  and (po.unique_post_id = t.on_post_id_c  or
                       po.created_by_id  = t.on_pat_id_c)
          -- where t.site_id = ?  -- this should be equivalent
          """
    runQueryFindMany(query, List(pageId.asAnyRef, siteId.asAnyRef), parseTag)
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
    val values = List(siteId.asAnyRef) :: tags.map(_.id.asAnyRef).toList
    runUpdate(statement, values)
  }


  private def parseTagType(rs: j_ResultSet): TagType = {
    TagType(
          id = getInt(rs, "id_c"),
          canTagWhat = getInt(rs, "can_tag_what_c"),
          urlSlug_unimpl = getOptString(rs, "url_slug_c"),
          dispName = getString(rs, "disp_name_c"),
          createdById = getInt(rs, "created_by_id_c"))(ifBad = Die)
  }

  private def parseTag(rs: j_ResultSet): Tag = {
    Tag(id = getInt(rs, "id_c"),
          tagTypeId = getInt(rs, "tagtype_id_c"),
          parentTagId_unimpl = getOptInt32(rs, "parent_tag_id_c"),
          onPatId = getOptInt32(rs, "on_pat_id_c"),
          onPostId = getOptInt32(rs, "on_post_id_c"))(ifBad = Die)
  }

}
