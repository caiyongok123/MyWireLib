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
package com.waz.sync.client

import com.waz.sync.client.OpenGraphClient.{OpenGraphData, OpenGraphDataResponse}
import com.waz.utils.wrappers.URI
import com.waz.znet.StringResponse
import org.scalatest.{FeatureSpec, Ignore, Matchers, RobolectricTests}

@Ignore class OpenGraphClientSpec extends FeatureSpec with Matchers with RobolectricTests {

  feature("Response parsing") {

    scenario("Parse proper OG response") {
      OpenGraphDataResponse.unapply(StringResponse(WireHtmlHeader)) shouldEqual
        Some(OpenGraphData("Wire — modern, private communication. For iOS, Android, OS X, Windows and web.",
          "HD quality calls, private and group chats with inline photos, music and video. Secure and perfectly synced across your devices.",
          Some(URI.parse("https://lh3.ggpht.com/gbxDT30ZwpwYMCF7ilrSaIpRQP3Z1Xdx2WUcyW5x_e8FDN8kA4CJGQQ0fFpVhKiGnPkAIOEf7S1_9cNi684Be-OY=s1024")),
          "website", Some(URI.parse("https://wire.com/"))))
    }

    scenario("Parse website without OG info") {
      OpenGraphDataResponse.unapply(StringResponse(GoogleWebsiteHeader)) shouldEqual None
    }

    scenario("Parse website with different meta format") {
      OpenGraphDataResponse.unapply(StringResponse(SampleWebsiteHeader)) shouldBe defined
    }

    scenario("Parse title from html tag if no og:title is present") {
      val info = OpenGraphDataResponse.unapply(StringResponse(HabraResponse))
      info shouldBe defined
      info.get.title shouldEqual "Интересные публикации / Хабрахабр"
      info.get.image should not be empty
    }

    scenario("Parse title from html tag with other attributes") {
      val info = OpenGraphDataResponse.unapply(StringResponse(FacebookResponse))
      info shouldBe defined
      info.get.title should not be empty
      info.get.image should not be empty
    }

    scenario("Parse title, image and description from Instagram") {
      val info = OpenGraphDataResponse.unapply(StringResponse(InstagramHeader))
      info shouldBe defined
      info.get.title should not be empty
      info.get.image should not be empty
      info.get.description should not be empty
    }

    scenario("Parse page from Blogger") {
      val info = OpenGraphDataResponse.unapply(StringResponse(BloggerHeader))
      info shouldBe defined
      info.get.title should not be empty
      info.get.image should not be empty
    }
  }

  val WireHtmlHeader =
    """
      |<!DOCTYPE html>
      |<html class="welcome " lang="en">
      |<head>
      |<meta http-equiv="content-type" content="text/html; charset=utf-8">
      |<meta charset="utf-8">
      |<meta name="apple-mobile-web-app-capable" content="yes">
      |<meta name="apple-mobile-web-app-status-bar-style" content="black">
      |<meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
      |<meta name="msapplication-TileColor" content="#fff">
      |<meta name="msapplication-TileImage" content="https://lh3.ggpht.com/1NvXpgCWdOwTrR36LArF1ScvBozs3eJMcD-KQ3yAGaqv-TIlRsoSzxeKn7ElFCbqqGJ2oOd98N_Xr0yA_W9lXnKU=s144">
      |<link rel="shortcut icon" href="/favicon.ico" sizes="48x48">
      |<link rel="icon" href="https://lh3.ggpht.com/1NvXpgCWdOwTrR36LArF1ScvBozs3eJMcD-KQ3yAGaqv-TIlRsoSzxeKn7ElFCbqqGJ2oOd98N_Xr0yA_W9lXnKU=s256" sizes="256x256" type="image/png">
      |<link rel="icon" href="https://lh3.ggpht.com/1NvXpgCWdOwTrR36LArF1ScvBozs3eJMcD-KQ3yAGaqv-TIlRsoSzxeKn7ElFCbqqGJ2oOd98N_Xr0yA_W9lXnKU=s128" sizes="128x128" type="image/png">
      |<link rel="icon" href="https://lh3.ggpht.com/1NvXpgCWdOwTrR36LArF1ScvBozs3eJMcD-KQ3yAGaqv-TIlRsoSzxeKn7ElFCbqqGJ2oOd98N_Xr0yA_W9lXnKU=s64" sizes="64x64" type="image/png">
      |<link rel="apple-touch-icon-precomposed icon" href="https://lh3.ggpht.com/1NvXpgCWdOwTrR36LArF1ScvBozs3eJMcD-KQ3yAGaqv-TIlRsoSzxeKn7ElFCbqqGJ2oOd98N_Xr0yA_W9lXnKU=s152" sizes="152x152" type="image/png">
      |<link rel="mask-icon" href="/p/img/brand/w-pinned.svg" color="#000">
      |<title>
      |    Wire — modern, private communication. For iOS, Android, OS X, Windows and web.
      |</title>
      |<meta name="twitter:site" content="@wire">
      |<meta name="twitter:card" content="summary_large_image">
      |<meta property="og:title" content="Wire — modern, private communication. For iOS, Android, OS X, Windows and web.">
      |<meta property="og:type" content="website">
      |<meta property="og:url" content="https://wire.com/">
      |<meta property="og:image" content="https://lh3.ggpht.com/gbxDT30ZwpwYMCF7ilrSaIpRQP3Z1Xdx2WUcyW5x_e8FDN8kA4CJGQQ0fFpVhKiGnPkAIOEf7S1_9cNi684Be-OY=s1024">
      |<link rel="stylesheet" href="/p/min/style/style.css?20160630t114206.393869911240347649">
      |<link rel="alternate" hreflang="en" href="https://wire.com/?hl=en">
      |<link rel="alternate" hreflang="de" href="https://wire.com/?hl=de">
      |<link rel="alternate" hreflang="x-default" href="https://wire.com/">
      |<meta property="og:description" name="description" content="HD quality calls, private and group chats with inline photos, music and video. Secure and perfectly synced across your devices.">
      |<script>
      |    (function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){
      |    (i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),
      |    m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)
      |    })(window,document,'script','//www.google-analytics.com/analytics.js','ga');
      |    ga('create', 'UA-56491034-3', 'auto');
      |    ga('send', 'pageview', {'dimension1': 'en'});
      |  </script>
      |</head>
    """.stripMargin

  val GoogleWebsiteHeader =
    """<!doctype html><html itemscope="" itemtype="http://schema.org/WebPage" lang="en-DE"><head><meta content="/images/branding/googleg/1x/googleg_standard_color_128dp.png" itemprop="image"><link href="/images/branding/product/ico/googleg_lodp.ico" rel="shortcut icon"><meta content="origin" id="mref" name="referrer"><title>Google</title></head>"""

  val SampleWebsiteHeader =
    """
      |<!DOCTYPE html><html><head><title data-react-helmet="true">Blood Orange Releases Freetown Sound: Listen | Pitchfork</title><meta data-react-helmet="true" name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no"/><meta data-react-helmet="true" name="description" content="The most trusted voice in music."/><meta data-react-helmet="true" name="og:site_name" content="Pitchfork"/><meta data-react-helmet="true" name="og:url" content="http://pitchfork.com"/><meta data-react-helmet="true" name="og:title" content="Blood Orange Releases Freetown Sound: Listen"/><meta data-react-helmet="true" name="og:description" content="Dev Hynes releases new album—with Debbie Harry, Empress Of, Carly Rae Jepsen, and more—three days ahead of schedule"/><meta data-react-helmet="true" name="og:type" content="article"/><meta data-react-helmet="true" name="og:image" content="http://cdn3.pitchfork.com/news/66031/28b578f1.jpeg"/><link data-react-helmet="true" rel="shortcut icon" type="image/png" href="http://cdn.pitchfork.com/assets/misc/favicon-32.png"/><link data-react-helmet="true" rel="icon" type="image/png" href="http://cdn.pitchfork.com/assets/misc/favicon-32.png" sizes="32x32"/><link data-react-helmet="true" rel="apple-touch-icon-precomposed" href="http://cdn.pitchfork.com/assets/misc/favicon-57-v2.png" sizes="57x57"/><link data-react-helmet="true" rel="apple-touch-icon-precomposed" href="http://cdn.pitchfork.com/assets/misc/favicon-152-v2.png" sizes="152x152"/><link data-react-helmet="true" rel="apple-touch-icon-precomposed" href="http://cdn.pitchfork.com/assets/misc/favicon-144-v2.png" sizes="144x144"/><link data-react-helmet="true" rel="apple-touch-icon-precomposed" href="http://cdn.pitchfork.com/assets/misc/favicon-120-v2.png" sizes="120x120"/><link data-react-helmet="true" rel="apple-touch-icon-precomposed" href="http://cdn.pitchfork.com/assets/misc/favicon-114-v2.png" sizes="114x114"/><link data-react-helmet="true" rel="apple-touch-icon-precomposed" href="http://cdn.pitchfork.com/assets/misc/favicon-72-v2.png" sizes="72x72"/><link data-react-helmet="true" rel="stylesheet" type="text/css" href="https://pitchfork-typefaces.s3.amazonaws.com/pitchfork-site/walfork_v3/walfork_v3.css"/><link data-react-helmet="true" rel="stylesheet" type="text/css" href="https://pitchfork-typefaces.s3.amazonaws.com/pitchfork-site/tiempos/tiempos.css"/><link data-react-helmet="true" rel="canonical" href="http://pitchfork.com/news/66031-blood-orange-releases-freetown-sound-listen/"/><script data-react-helmet="true" type="application/ld+json">{"@context":"http://schema.org","@type":"NewsArticle","headline":"Blood Orange Releases Freetown Sound: Listen","url":"http://pitchfork.com/news/66031-blood-orange-releases-freetown-sound-listen/","thumbnailUrl":"http://cdn3.pitchfork.com/news/66031/28b578f1.jpeg","dateCreated":"2016-06-28T04:41:00.000Z","articleSection":"news","creator":"Jazz Monroe","keywords":["Blood Orange","New Releases","Audio","Rock","Electronic"]}</script><link rel="stylesheet" type="text/css" href="http://cdn.pitchfork.com/assets/builds/main-6a345aeabbe5c93e0f7f.css"/><script>(function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){
      |    (i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),
      |    m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)
      |    })(window,document,'script','//www.google-analytics.com/analytics.js','ga');
      |
      |    ga('create', 'UA-535622-1', 'auto');
      |</script><script>
      |    var _qevents = _qevents || [];
      |    (function() {
      |        var elem = document.createElement('script');
      |        elem.src = (document.location.protocol == "https:" ? "https://secure" : "http://edge") + ".quantserve.com/quant.js";
      |        elem.async = true;
      |        elem.type = "text/javascript";
      |        var scpt = document.getElementsByTagName('script')[0];
      |        scpt.parentNode.insertBefore(elem, scpt);
      |    })();
      |
      |    _qevents.push(
      |        {qacct:"p-70RhgKmunzjBs",
      |         labels:"Music,Pop Culture"},
      |        {qacct:"p-d5PgAY9MthZsM"}
      |    );
      |</script><script type="text/javascript">window.DEPLOYMENT_CONTEXT="production"</script><script type="text/javascript" src="//assets.adobedtm.com/6372cf21ef88ee60bc2977a4898dcb5c7945a212/satelliteLib-db38f058255bb4dd2252e85fb3a91ae2af7c2087.js"></script></head>"""

  val HabraResponse =
    """
      |<!DOCTYPE html>
      |<html>
      |<head>
      |<meta http-equiv="content-type" content="text/html; charset=utf-8">
      |<meta http-equiv="content-type" content="text/html; charset=utf-8">
      |<meta content="width=1024" name="viewport">
      |<title>Интересные публикации / Хабрахабр</title>
      |<meta property="og:image" content="https://habrahabr.ru/i/habralogo.jpg">
      |<link rel="image_src" href="https://habrahabr.ru/i/habralogo.jpg">
      |<meta name="yandex-verification" content="71593b225aeafc4e">
      |<meta name="referrer" content="unsafe-url">
      |<meta name="pocket-site-verification" content="ed24b2b9721edf0a282c5b4a3232c4">
      |<link href="//habracdn.net/habr/styles/1467900147/_parts/posts.css" rel="stylesheet" media="all">
      |<link href="//habracdn.net/habr/styles/1467900147/forms.css" rel="stylesheet" media="all">
      |<link href="//habracdn.net/habr/styles/1467900147/hubs/all.css" rel="stylesheet" media="all">
      |<link href="//habracdn.net/habr/styles/1467900147/_build/global_main.css" rel="stylesheet" media="all">
      |<meta content="Хабрахабр" name="apple-mobile-web-app-title">
      |<link rel="apple-touch-icon-precomposed" sizes="57x57" href="/images/favicons/apple-touch-icon-57x57.png">
      |<link rel="apple-touch-icon-precomposed" sizes="114x114" href="/images/favicons/apple-touch-icon-114x114.png">
      |<link rel="apple-touch-icon-precomposed" sizes="72x72" href="/images/favicons/apple-touch-icon-72x72.png">
      |<link rel="apple-touch-icon-precomposed" sizes="144x144" href="/images/favicons/apple-touch-icon-144x144.png">
      |<link rel="apple-touch-icon-precomposed" sizes="120x120" href="/images/favicons/apple-touch-icon-120x120.png">
      |<link rel="apple-touch-icon-precomposed" sizes="152x152" href="/images/favicons/apple-touch-icon-152x152.png">
      |<link rel="icon" type="image/png" href="/images/favicons/favicon-32x32.png" sizes="32x32">
      |<link rel="icon" type="image/png" href="/images/favicons/favicon-16x16.png" sizes="16x16">
      |<meta name="application-name" content="Хабрахабр">
      |<meta name="msapplication-TileColor" content="#FFFFFF">
      |<meta name="msapplication-TileImage" content="mstile-144x144.png">
      |</head>
    """.stripMargin

  val FacebookResponse =
    """
      |<!DOCTYPE html>
      |<html lang="en" id="facebook" class="no_js">
      |<head>
      |<meta http-equiv="content-type" content="text/html; charset=utf-8">
      |<meta charset="utf-8">
      |<meta name="referrer" content="default" id="meta_referrer">
      |<script>function envFlush(a){function b(c){for(var d in a)c[d]=a[d];}if(window.requireLazy){window.requireLazy(['Env'],b);}else{window.Env=window.Env||{};b(window.Env);}}envFlush({"ajaxpipe_token":"AXh_BB2SMciRO6N7","lhsh":"bAQFbSk8y","timeslice_heartbeat_config":{"pollIntervalMs":33,"idleGapThresholdMs":60,"ignoredTimesliceNames":{"requestAnimationFrame":true},"enableOnRequire":false}});</script><script>CavalryLogger=false;</script><title id="pageTitle">Facebook - Log In or Sign Up</title>
      |<meta property="og:site_name" content="Facebook">
      |<meta property="og:url" content="https://www.facebook.com/">
      |<meta property="og:image" content="https://www.facebook.com/images/fb_icon_325x325.png">
      |<meta property="og:locale" content="en_US">
      |<meta property="og:locale:alternate" content="www">
      |<meta property="og:locale:alternate" content="es_LA">
      |<meta property="og:locale:alternate" content="es_ES">
      |<meta property="og:locale:alternate" content="fr_FR">
      |<meta property="og:locale:alternate" content="it_IT">
      |<meta property="og:locale:alternate" content="id_ID">
      |<meta property="og:locale:alternate" content="th_TH">
      |<meta property="og:locale:alternate" content="vi_VN">
      |<meta property="og:locale:alternate" content="ko_KR">
      |<meta property="og:locale:alternate" content="ja_JP">
      |<script type="application/ld+json">{"\u0040context":"http:\/\/schema.org","\u0040type":"WebSite","name":"Facebook","url":"https:\/\/www.facebook.com\/"}</script><link rel="search" type="application/opensearchdescription+xml" href="/osd.xml" title="Facebook">
      |<link rel="canonical" href="https://www.facebook.com/">
      |<link rel="alternate" media="only screen and (max-width: 640px)" href="https://m.facebook.com/">
      |<link rel="alternate" media="handheld" href="https://m.facebook.com/">
      |<link rel="alternate" hreflang="x-default" href="https://www.facebook.com/">
      |<link rel="alternate" hreflang="en" href="https://www.facebook.com/">
      |<link rel="alternate" hreflang="ar" href="https://ar-ar.facebook.com/">
      |<link rel="alternate" hreflang="bg" href="https://bg-bg.facebook.com/">
      |<link rel="alternate" hreflang="bs" href="https://bs-ba.facebook.com/">
      |<link rel="alternate" hreflang="ca" href="https://ca-es.facebook.com/">
      |<link rel="alternate" hreflang="da" href="https://da-dk.facebook.com/">
      |<link rel="alternate" hreflang="el" href="https://el-gr.facebook.com/">
      |<link rel="alternate" hreflang="es" href="https://es-la.facebook.com/">
      |<link rel="alternate" hreflang="es-es" href="https://es-es.facebook.com/">
      |<link rel="alternate" hreflang="fa" href="https://fa-ir.facebook.com/">
      |<link rel="alternate" hreflang="fi" href="https://fi-fi.facebook.com/">
      |<link rel="alternate" hreflang="fr" href="https://fr-fr.facebook.com/">
      |<link rel="alternate" hreflang="fr-ca" href="https://fr-ca.facebook.com/">
      |<link rel="alternate" hreflang="hi" href="https://hi-in.facebook.com/">
      |<link rel="alternate" hreflang="hr" href="https://hr-hr.facebook.com/">
      |<link rel="alternate" hreflang="id" href="https://id-id.facebook.com/">
      |<link rel="alternate" hreflang="it" href="https://it-it.facebook.com/">
      |<link rel="alternate" hreflang="ko" href="https://ko-kr.facebook.com/">
      |<link rel="alternate" hreflang="mk" href="https://mk-mk.facebook.com/">
      |<link rel="alternate" hreflang="ms" href="https://ms-my.facebook.com/">
      |<link rel="alternate" hreflang="pl" href="https://pl-pl.facebook.com/">
      |<link rel="alternate" hreflang="pt" href="https://pt-br.facebook.com/">
      |<link rel="alternate" hreflang="pt-pt" href="https://pt-pt.facebook.com/">
      |<link rel="alternate" hreflang="ro" href="https://ro-ro.facebook.com/">
      |<link rel="alternate" hreflang="sl" href="https://sl-si.facebook.com/">
      |<link rel="alternate" hreflang="sr" href="https://sr-rs.facebook.com/">
      |<link rel="alternate" hreflang="th" href="https://th-th.facebook.com/">
      |<link rel="alternate" hreflang="vi" href="https://vi-vn.facebook.com/">
      |<meta name="description" content="Create an account or log into Facebook. Connect with friends, family and other people you know. Share photos and videos, send messages and get updates.">
      |<meta name="robots" content="noodp,noydir">
      |<link rel="shortcut icon" href="https://static.xx.fbcdn.net/rsrc.php/yV/r/hzMapiNYYpW.ico">
      |<link type="text/css" rel="stylesheet" href="https://static.xx.fbcdn.net/rsrc.php/v2/yp/r/q7IRNgVMrII.css" data-bootloader-hash="lKz4h" data-permanent="1" crossorigin="anonymous">
      |<link type="text/css" rel="stylesheet" href="https://static.xx.fbcdn.net/rsrc.php/v2/yQ/r/g6_SC5FY4N_.css" data-bootloader-hash="44wiD" crossorigin="anonymous">
      |<link type="text/css" rel="stylesheet" href="https://static.xx.fbcdn.net/rsrc.php/v2/yK/r/Afr-nAwy4we.css" data-bootloader-hash="2ihRB" data-permanent="1" crossorigin="anonymous">
      |<link type="text/css" rel="stylesheet" href="https://static.xx.fbcdn.net/rsrc.php/v2/yR/r/6A3lwW0Dqhw.css" data-bootloader-hash="TK4Jr" data-permanent="1" crossorigin="anonymous">
      |<script src="https://static.xx.fbcdn.net/rsrc.php/v2/yx/r/rtsCiuA4RET.js" data-bootloader-hash="HCLO2" crossorigin="anonymous"></script><script>require("TimeSlice").guard(function() {(require("ServerJSDefine")).handleDefines([["VideoPlayerAbortLoadingExperiment",[],{"canAbort":false},824],["GroupsProductDetailGating",[],{"tuzi_dialog":null},1461],["MarauderConfig",[],{"app_version":2433342,"gk_enabled":false},31],["PageTransitionsConfig",[],{"reloadOnBootloadError":false},1067],["PageNavigationStageLoggerGK",[],{"gk_check":false},1434],["ErrorSignalConfig",[],{"uri":"https:\/\/error.facebook.com\/common\/scribe_endpoint.php"},319],["ZeroRewriteRules",[],{},1478],["BigPipeExperiments",[],{"preparse_content":"","download_js":"blocked_by_dom_ready","link_images_to_pagelets":false},907],["LSD",[],{"token":"AVowDACV"},323],["ISB",[],{},330],["FbtLogger",[],{"logger":null},288],["FunnelLoggerConfig",[],{"freq":{"WWW_CANVAS_AD_CREATION_FUNNEL":1,"WWW_CANVAS_EDITOR_FUNNEL":1,"WWW_LINK_PICKER_DIALOG_FUNNEL":1,"WWW_MEME_PICKER_DIALOG_FUNNEL":1,"WWW_LEAD_GEN_FORM_CREATION_FUNNEL":1,"WWW_LEAD_GEN_DESKTOP_AD_UNIT_FUNNEL":1,"WWW_CAMPFIRE_COMPOSER_UPSELL_FUNNEL":1,"WWW_RECRUITING_SEARCH_FUNNEL":1,"WWW_EXAMPLE_FUNNEL":1,"WWW_REACTIONS_NUX_FUNNEL":1,"MSITE_EXAMPLE_FUNNEL":10,"WWW_FEED_SHARE_DIALOG_FUNNEL":100,"MSITE_FEED_SHARE_DIALOG_FUNNEL":100,"MSITE_COMMENT_TYPING_FUNNEL":500,"WWW_SEARCH_AWARENESS_LEARNING_NUX_FUNNEL":1,"WWW_CONSTITUENT_TITLE_UPSELL_FUNNEL":1,"MTOUCH_FEED_MISSED_STORIES_FUNNEL":10,"WWW_UFI_SHARE_LINK_FUNNEL":1,"WWW_CMS_SEARCH_FUNNEL":1,"GAMES_QUICKSILVER_FUNNEL":1,"default":1000}},1271],["BootloaderConfig",[],{"maxJsRetries":0,"jsRetries":null,"jsRetryAbortNum":2,"jsRetryAbortTime":5,"payloadEndpointURI":"https:\/\/www.facebook.com\/ajax\/haste-response\/"},329],["CurrentCommunityInitialData",[],{},490],["PhotoSnowliftActionsGating",[],{"ALLOW_MAKE_COVER_PHOTO_BUTTON":false,"ALLOW_MAKE_PROFILE_PICTURE_BUTTON":false},887],["CurrentUserInitialData",[],{"USER_ID":"0","ACCOUNT_ID":"0"},270],["URLFragmentPreludeConfig",[],{"incorporateQuicklingFragment":true,"hashtagRedirect":true},137],["TrackingConfig",[],{"domain":"https:\/\/pixel.facebook.com"},325],["ZeroCategoryHeader",[],{},1127],["ServerNonce",[],{"ServerNonce":"sYkdFn4-ejAUoC-toKbRs7"},141],["CSSLoaderConfig",[],{"timeout":5000,"modulePrefix":"BLCSS:"},619],["InitialServerTime",[],{"serverTime":1467969330000},204],["DTSGInitialData",[],{},258],["SiteData",[],{"revision":2433342,"tier":"","push_phase":"V3","pkg_cohort":"PHASED:DEFAULT","pkg_cohort_key":"__pc","haste_site":"www","be_mode":-1,"be_key":"__be","is_rtl":false,"vip":"2a03:2880:2040:7f83:face:b00c:0:25de"},317],["UserAgentData",[],{"browserArchitecture":"32","browserFullVersion":null,"browserMinorVersion":null,"browserName":"Unknown","browserVersion":null,"deviceName":"Unknown","engineName":"Unknown","engineVersion":null,"platformArchitecture":"32","platformName":"Unknown","platformVersion":null,"platformFullVersion":null},527],["LinkshimHandlerConfig",[],{"supports_meta_referrer":false,"default_meta_referrer_policy":"default","switched_meta_referrer_policy":"origin","render_verification_rate":1000,"link_react_default_hash":"dAQGnCHqX","linkshim_host":"l.facebook.com"},27],["WebSpeedExperiments",[],{"non_blocking_tracker":false,"non_blocking_logger":false},1160],["LinkReactUnsafeHrefConfig",[],{"LinkHrefChecker":null},1182],["BanzaiConfig",[],{"EXPIRY":86400000,"MAX_SIZE":10000,"MAX_WAIT":150000,"RESTORE_WAIT":150000,"blacklist":["time_spent"],"gks":{"boosted_component":true,"boosted_pagelikes":true,"boosted_posts":true,"boosted_website":true,"jslogger":true,"mercury_send_error_logging":true,"pages_client_logging":true,"platform_oauth_client_events":true,"useraction":true,"videos":true,"visibility_tracking":true,"vitals":true,"graphexplorer":true,"gqls_web_logging":true}},7],["ReactGK",[],{"logTopLevelRenders":false,"useCreateElement":true},998],["CoreWarningGK",[],{"forceWarning":false},725],["FbtQTOverrides",[],{"overrides":{"1_65c3391ebe4a1af8364ca4fbb8cb54d1":"Mobile Number or Email:","1_9171ad6e2268759887b1b45d16139587":"Reach Even More People"}},551],["FbtResultGK",[],{"shouldReturnFbtResult":false,"inlineMode":"NO_INLINE"},876],["IntlViewerContext",[],{"GENDER":50331648},772],["IntlPhonologicalRules",[],{"meta":{"\/_B\/":"([.,!?\\s]|^)","\/_E\/":"([.,!?\\s]|$)"},"patterns":{"\/\u0001(.*)('|&#039;)s\u0001(?:'|&#039;)s(.*)\/":"\u0001$1$2s\u0001$3","\/_\u0001([^\u0001]*)\u0001\/":"javascript"}},1496],["WWWBase",[],{"uri":"https:\/\/www.facebook.com\/"},318],["AsyncRequestConfig",[],{"retryOnNetworkError":"1","logAsyncRequest":false},328],["SessionNameConfig",[],{"seed":"1vMH"},757]]);new (require("ServerJS"))().handle({"require":[["TimeSlice"],["markJSEnabled"],["lowerDomain"],["URLFragmentPrelude"],["Primer"],["BigPipe"],["Bootloader"],["TimeSlice","disableHeartbeat",[],[],[]]]});}, "ServerJS define", {"root":true})();</script>
      |</head>
    """.stripMargin

  val InstagramHeader =
    """
      |<head>
      |<link rel="canonical" href="https://www.instagram.com/p/BICaRJ4hDuW/" />
      |<meta content="See this Instagram photo by @meetgarfi • 2,499 likes" name="description" />
      |<meta property="og:site_name" content="Instagram" />
      |<meta property="og:title" content="Instagram photo by Garfi - Angry Cat • Jul 19, 2016 at 9:15am UTC" />
      |<meta property="og:image" content="https://scontent-frt3-1.cdninstagram.com/t51.2885-15/s750x750/sh0.08/e35/13694889_959602174151095_181016288_n.jpg?ig_cache_key=MTI5NzcxNTE3MDY4ODM4Mzg5NA%3D%3D.2" />
      |<meta property="og:description" content="See this Instagram photo by @meetgarfi • 2,499 likes" />
      |<meta property="fb:app_id" content="124024574287414" />
      |<meta property="og:url" content="https://www.instagram.com/p/BICaRJ4hDuW/" />
      |<meta property="instapp:owner_user_id" content="1523718919" />
      |<meta property="al:ios:app_name" content="Instagram" />
      |<meta property="al:ios:app_store_id" content="389801252" />
      |<meta property="al:ios:url" content="instagram://media?id=1297715170688383894" />
      |<meta property="al:android:app_name" content="Instagram" />
      |<meta property="al:android:package" content="com.instagram.android" />
      |<meta property="al:android:url" content="https://www.instagram.com/p/BICaRJ4hDuW/" />
      |<meta name="medium" content="image" />
      |<meta property="og:type" content="instapp:photo" />
      |</head>
    """.stripMargin


  val BloggerHeader =
    """
      |<!DOCTYPE html>
      |<html class='v2 detail-page' dir='ltr' itemscope='' itemtype='http://schema.org/Blog' lang='en' xmlns='http://www.w3.org/1999/xhtml' xmlns:b='http://www.google.com/2005/gml/b' xmlns:data='http://www.google.com/2005/gml/data' xmlns:expr='http://www.google.com/2005/gml/expr'>
      |<head>
      |<title>
      |Official Blogger Blog: More custom template flexibility
      |</title>
      |<meta content='width=device-width, height=device-height, minimum-scale=1.0, initial-scale=1.0, user-scalable=0' name='viewport'/>
      |<meta content='IE=Edge' http-equiv='X-UA-Compatible'/>
      |<meta content='article' property='og:type'/>
      |<meta content='More custom template flexibility' property='og:title'/>
      |<meta content='en_US' property='og:locale'/>
      |<meta content='https://blogger.googleblog.com/2016/05/more-custom-template-flexibility.html' property='og:url'/>
      |<meta content='Official Blogger Blog' property='og:site_name'/>
      |<!-- Twitter Card properties -->
      |<meta content='Official Blogger Blog' property='twitter:site'/>
      |<meta content='More custom template flexibility' property='twitter:title'/>
      |<meta content='https://lh4.googleusercontent.com/VWNK3317-R03RHjg7wFXk5IzVj0CEUMn3FNIZhJaW5bOeP_8II7zSkzJjXhJh8Rw3MnnOEXBw6fIBzpvOvof0zE18gQriX5BPyOt73Z7AwTvK7M8AUOAuXz0ZqS5e8_juz43dmtS=s72-c' property='twitter:image'/>
      |<meta content='summary' name='twitter:card'/>
      |<meta content='@blogger' name='twitter:creator'/>
      |<link href='https://fonts.googleapis.com/css?family=Roboto:400italic,400,500,500italic,700,700italic' rel='stylesheet' type='text/css'/>
      |<link href='https://fonts.googleapis.com/icon?family=Material+Icons' rel='stylesheet'/>
      |<script src='https://ajax.googleapis.com/ajax/libs/jquery/1.11.3/jquery.min.js' type='text/javascript'></script>
      |<!-- End -->
      |<link type='text/css' rel='stylesheet' href='https://www.blogger.com/static/v1/widgets/434580396-css_bundle_v2.css' />
      |<link type='text/css' rel='stylesheet' href='https://www.blogger.com/dyn-css/authorization.css?targetBlogID=2399953&zx=fb409d02-8bb7-4951-affb-22f5563c8f84' />
      |</head>
    """.stripMargin
}
