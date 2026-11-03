package com.johnny.wxscript.ui.market

import android.content.Context
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.items.script.market.PluginMarketClient
import h.Hchat.hooks.items.script.market.PluginMarketCommentMutation
import h.Hchat.hooks.items.script.market.PluginMarketCommentPage
import h.Hchat.hooks.items.script.market.PluginMarketInstaller
import h.Hchat.hooks.items.script.market.PluginMarketLikeResult
import h.Hchat.hooks.items.script.market.PluginMarketNotificationPage
import h.Hchat.hooks.items.script.market.PluginMarketOwnership
import h.Hchat.hooks.items.script.market.PluginMarketSettings
import h.Hchat.hooks.items.script.market.PluginMarketUserIdentity

/**
 * 市场社交层：点赞 / 关注 / 评论 / 通知 / 上传发布。
 *
 * 这些接口都要「当前微信账号身份」，而账号 API 依赖 DexKit 引导完成。
 * 所以拿不到身份时统一抛出可读错误，界面据此禁用按钮并引导去诊断页，
 * 而不是静默当成「未点赞 / 没有评论」。
 */
object MarketSocial {

    fun identity(): Result<PluginMarketUserIdentity> = runCatching {
        val account = WeChatApis.account()
            ?: error("微信账号 API 尚未就绪，请到「脚本诊断」查看引导状态")
        val wxId = account.selfWxId().trim()
        require(wxId.isNotBlank()) { "读取不到当前账号 wxid，请重启微信后重试" }
        val contact = runCatching { WeChatApis.contacts()?.getContact(wxId) }.getOrNull()
        PluginMarketUserIdentity(
            wxId = wxId,
            weChatId = account.customWxId().trim().ifBlank { contact?.customWxId.orEmpty().trim() },
            nickname = account.selfName().trim().ifBlank { contact?.nickname.orEmpty().trim() },
        )
    }

    private fun requireIdentity(): PluginMarketUserIdentity =
        identity().getOrElse { throw it }

    // ───────────────────────────── 点赞 ─────────────────────────────

    fun likeStatus(context: Context, remotePluginId: String): Result<PluginMarketLikeResult> = runCatching {
        PluginMarketClient.likeStatus(context, remotePluginId, requireIdentity()).getOrThrow()
    }

    fun setLiked(context: Context, remotePluginId: String, liked: Boolean): Result<PluginMarketLikeResult> =
        runCatching {
            val id = requireIdentity()
            if (liked) PluginMarketClient.like(context, remotePluginId, id).getOrThrow()
            else PluginMarketClient.unlike(context, remotePluginId, id).getOrThrow()
        }

    // ───────────────────────────── 关注 ─────────────────────────────

    fun followStatus(context: Context, remotePluginId: String): Result<Boolean> = runCatching {
        PluginMarketClient.followStatus(context, remotePluginId, requireIdentity()).getOrThrow()
    }

    fun setFollowing(context: Context, remotePluginId: String, following: Boolean): Result<Boolean> =
        runCatching {
            val id = requireIdentity()
            if (following) PluginMarketClient.follow(context, remotePluginId, id).getOrThrow()
            else PluginMarketClient.unfollow(context, remotePluginId, id).getOrThrow()
        }

    // ───────────────────────────── 评论 ─────────────────────────────

    fun comments(context: Context, remotePluginId: String, limit: Int = 50): Result<PluginMarketCommentPage> =
        runCatching {
            val id = identity().getOrNull()
            PluginMarketClient.comments(context, remotePluginId, id, limit).getOrThrow()
        }

    fun comment(context: Context, remotePluginId: String, content: String): Result<PluginMarketCommentMutation> =
        runCatching {
            PluginMarketClient.addComment(context, remotePluginId, requireIdentity(), content).getOrThrow()
        }

    fun reply(
        context: Context,
        remotePluginId: String,
        parentCommentId: String,
        content: String,
    ): Result<PluginMarketCommentMutation> = runCatching {
        PluginMarketClient.replyComment(context, remotePluginId, parentCommentId, requireIdentity(), content)
            .getOrThrow()
    }

    fun deleteComment(
        context: Context,
        remotePluginId: String,
        commentId: String,
    ): Result<PluginMarketCommentMutation> = runCatching {
        PluginMarketClient.deleteComment(context, remotePluginId, commentId, requireIdentity()).getOrThrow()
    }

    // ───────────────────────────── 通知 ─────────────────────────────

    fun notifications(context: Context, limit: Int = 100): Result<PluginMarketNotificationPage> = runCatching {
        PluginMarketClient.notifications(context, requireIdentity(), limit).getOrThrow()
    }

    fun markNotificationsRead(context: Context): Result<Int> = runCatching {
        PluginMarketClient.markNotificationsRead(context, requireIdentity()).getOrThrow().markedCount.toInt()
    }

    // ───────────────────────────── 上传发布 ─────────────────────────────

    /**
     * 把本地插件发布到市场（或更新自己已发布的插件）。
     *
     * 本地若有 ownerToken（[PluginMarketSettings.ownership]），按「更新」提交；
     * 没有则按「新建」提交 —— 服务端据此决定覆盖还是新建。
     */
    fun upload(
        context: Context,
        localPluginId: String,
        marketName: String,
        releaseNotes: String,
    ): Result<PluginMarketOwnership> = runCatching {
        val packageData = PluginMarketInstaller.collectUploadPackage(
            context = context,
            pluginId = localPluginId,
            remoteName = marketName,
            releaseNotes = releaseNotes,
        ).getOrThrow()
        val existing = runCatching { PluginMarketSettings.ownership(context, localPluginId) }.getOrNull()
        PluginMarketClient.upload(context, packageData, existing).getOrThrow()
    }
}
