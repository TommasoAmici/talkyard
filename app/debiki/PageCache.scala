/**
 * Copyright (c) 2011-2012 Kaj Magnus Lindberg (born 1979)
 */

package debiki

import com.debiki.v0.Prelude._
import com.debiki.v0.{liftweb => lw}
import com.google.{common => guava}
import controllers.PageRequest
import debiki.DebikiHttp.throwNotFound
import java.{util => ju}
import play.api.Logger
import scala.xml.NodeSeq
import PageCache._
import com.debiki.v0._


object PageCache {
  case class Key(tenantId: String, pageGuid: String, hostAndPort: String)
}


/**
 * Caches serialized pages, if the root post is the page body.
 *
 * Each page should always be accessed via the same hostAndPort,
 * otherwise `refreshLater` fails to refresh all cached versions of
 * the page.
 *
 * Uses `PageRequest.dao` to load pages from the database.
 */
class PageCache {

  /**
   * Passes the current quota consumer to _loadAndRender. Needed because
   * Google Guava's cache lookup method takes an unchangeable key only,
   * but we need to use different TenantDao:s when loading pages,
   * so the correct tenant's quota is consumed.
   */
  private val _tenantDaoDynVar =
    new util.DynamicVariable[TenantDao](null)


  private val _pageCache: ju.concurrent.ConcurrentMap[Key, NodeSeq] =
    new guava.collect.MapMaker().
       softValues().
       maximumSize(100*1000).
       //expireAfterWrite(10. TimeUnits.MINUTES).
       makeComputingMap(new guava.base.Function[Key, NodeSeq] {
      def apply(k: Key): NodeSeq = {
        val tenantDao = _tenantDaoDynVar.value
        assert(tenantDao ne null)
        _loadAndRender(k, PageRoot.TheBody, tenantDao)
      }
    })


  private def _loadAndRender(k: Key, pageRoot: PageRoot, tenantDao: TenantDao)
        : NodeSeq = {
    assert(k.tenantId == tenantDao.tenantId)
    tenantDao.loadPage(k.pageGuid) match {
      case Some(page) if page.body.map(_.someVersionApproved) != Some(true) =>
        // Regrettably, currently the page is hidden also for admins (!).
        // But right now only admins can create new pages and they are
        // auto approved (well, will be, in the future.)
        <p>This page is pending approval.</p>
      case Some(debate) =>
        val config = DebikiHttp.newUrlConfig(k.hostAndPort)
        // Hmm, DebateHtml and pageTrust should perhaps be wrapped in
        // some PageRendererInput class, that is handled to PageCache,
        // so PageCache don't need to know anything about how to render
        // a page. But for now:
        val pageTrust = PageTrust(debate)
        // layoutPage() takes long, because markup source is converted to html.
        val nodes =
          DebateHtml(debate, pageTrust).configure(config).layoutPage(pageRoot)
        nodes map { html =>
        // The html is serialized here only once, then it's added to the
        // page cache (if pageRoot is the Page.body -- see get() below).
          xml.Unparsed(lw.Html5.toString(html))
        }
      case None =>
        // Page missing. Should have been noticed during access control.
        assErr("DwE35eQ20", "Page "+ safed(k.pageGuid) +" not found")
    }
  }


  def get(pageReq: PageRequest[_], pageRoot: PageRoot): NodeSeq = {
    try {
      val config = DebikiHttp.newUrlConfig(pageReq)
      // The dialog templates includes the user name and cannot currently
      // be cached.
      val templates = FormHtml(config, pageReq.xsrfToken.token,
        pageRoot, pageReq.permsOnPage).dialogTemplates
      val key = Key(
        pageReq.tenantId, pageReq.pagePath.pageId.get, pageReq.request.host)
      pageRoot match {
        case PageRoot.Real(Page.BodyId) =>
          _tenantDaoDynVar.withValue(pageReq.dao) {
            // The page (with the article and all comments) includes
            // nothing user specific and can thus be cached.
            val page = _pageCache.get(key)
            page ++ templates
          }

        case otherRoot =>
          // The cache currently works only for the page body as page root.
          _loadAndRender(key, otherRoot, pageReq.dao) ++ templates
      }
    } catch {
      case e: NullPointerException =>
        // Another user and thread might remove the page at any time?
        // COULD create a DebikiLogger.warnThrowNotFound(...)
        Logger.warn("Page "+ safed(pageReq.pagePath) +" not found")
        throwNotFound("DwE091k5J83", "Page "+ safed(pageReq.pagePath) +" not found")
    }
  }


  def refreshLater(tenantId: String, pageId: String, host: String) {
    // For now, simply drop the cache entry.
    // COULD send a message to an actor that refreshes the page later.
    // Then the caller won't have to wait.
    // COULD fix BUG: only clears the cache for the current host and port
    // (problems e.g. if you use localhost:8080 and <ip>:8080).
    _pageCache.remove(Key(tenantId, pageId, host))
  }

}

