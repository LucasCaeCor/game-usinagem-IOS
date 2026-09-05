@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package br.com.usinagemmaster.game.domain

import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSUserDefaults

private const val COMMUNITY_CACHE_KEY = "usinagemmaster.online.community.cache"
private const val COMMUNITY_PUBLISH_KEY = "usinagemmaster.online.community.publish"
private const val COMMUNITY_HIRE_KEY = "usinagemmaster.online.community.hire"
private const val COMMUNITY_OFFER_PUBLISH_KEY = "usinagemmaster.online.community.offer.publish"
private const val COMMUNITY_RENTAL_OPERATION_KEY = "usinagemmaster.online.community.rental.operation"
private const val COMMUNITY_REFRESH_NOTIFICATION = "UsinagemCommunityRefresh"
private const val COMMUNITY_PUBLISH_NOTIFICATION = "UsinagemCommunityPublish"
private const val COMMUNITY_HIRE_NOTIFICATION = "UsinagemCommunityHire"
private const val COMMUNITY_OFFER_PUBLISH_NOTIFICATION = "UsinagemCommunityOfferPublish"
private const val COMMUNITY_OFFER_WITHDRAW_NOTIFICATION = "UsinagemCommunityOfferWithdraw"
private const val COMMUNITY_RENTAL_OPERATION_NOTIFICATION = "UsinagemCommunityRentalOperation"

actual fun onlineCommunityRaw(): String =
    NSUserDefaults.standardUserDefaults.stringForKey(COMMUNITY_CACHE_KEY).orEmpty()

actual fun requestOnlineCommunityRefresh() {
    NSNotificationCenter.defaultCenter.postNotificationName(COMMUNITY_REFRESH_NOTIFICATION, null)
}

actual fun publishOnlineFactory(raw: String) {
    NSUserDefaults.standardUserDefaults.setObject(raw, forKey = COMMUNITY_PUBLISH_KEY)
    NSNotificationCenter.defaultCenter.postNotificationName(COMMUNITY_PUBLISH_NOTIFICATION, null)
}

actual fun hireOnlineCharacter(ownerUid: String) {
    NSUserDefaults.standardUserDefaults.setObject(ownerUid, forKey = COMMUNITY_HIRE_KEY)
    NSNotificationCenter.defaultCenter.postNotificationName(COMMUNITY_HIRE_NOTIFICATION, null)
}

actual fun publishOnlineCharacterOffer(raw: String) {
    NSUserDefaults.standardUserDefaults.setObject(raw, forKey = COMMUNITY_OFFER_PUBLISH_KEY)
    NSNotificationCenter.defaultCenter.postNotificationName(COMMUNITY_OFFER_PUBLISH_NOTIFICATION, null)
}

actual fun withdrawOnlineCharacterOffer() {
    NSNotificationCenter.defaultCenter.postNotificationName(COMMUNITY_OFFER_WITHDRAW_NOTIFICATION, null)
}

actual fun operateOnlineRental(raw: String) {
    NSUserDefaults.standardUserDefaults.setObject(raw, forKey = COMMUNITY_RENTAL_OPERATION_KEY)
    NSNotificationCenter.defaultCenter.postNotificationName(COMMUNITY_RENTAL_OPERATION_NOTIFICATION, null)
}
