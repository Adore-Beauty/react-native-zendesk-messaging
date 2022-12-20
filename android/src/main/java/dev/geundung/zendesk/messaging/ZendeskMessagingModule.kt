package dev.geundung.zendesk.messaging

import android.content.Intent
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import zendesk.android.events.ZendeskEvent
import zendesk.android.pageviewevents.PageView
import zendesk.messaging.android.DefaultMessagingFactory

class ZendeskMessagingModule(private val reactContext: ReactApplicationContext) :
  ReactContextBaseJavaModule(reactContext) {
  private val module: ZendeskNativeModule = ZendeskNativeModule.getInstance()
  private var initialized = false

  override fun getName(): String {
    return NAME
  }

  private fun sendEvent(eventName: String, params: WritableMap?) {
    reactContext
      .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
      .emit(eventName, params)
  }

  private fun setupEventObserver() {
    module.addEventListener(
      listener = {
          zendeskEvent ->
        when (zendeskEvent) {
          is ZendeskEvent.UnreadMessageCountChanged -> {
            val event: WritableMap = Arguments.createMap()
            event.putDouble("unreadCount", zendeskEvent.currentUnreadCount.toDouble())
            sendEvent("unreadMessageCountChanged", event)
          }
          is ZendeskEvent.AuthenticationFailed -> {
            val event: WritableMap = Arguments.createMap()
            event.putString("reason", zendeskEvent.error.message)
            sendEvent("authenticationFailed", event)
          }
          else -> {}
        }
      },
    )
  }

  @ReactMethod
  fun initialize(config: ReadableMap, promise: Promise) {
    if (initialized) {
      promise.resolve(null)
      return
    }

    val channelKey = config.getString("channelKey")
    if (channelKey == null || channelKey.isEmpty()) {
      promise.reject(null, "channelKey is empty")
      return
    }

    module.initialize(
      context = reactContext,
      channelKey = channelKey,
      successCallback = { _ ->
        setupEventObserver()
        initialized = true
        promise.resolve(null)
      },
      failureCallback = { error -> promise.reject(error) },
      messagingFactory = DefaultMessagingFactory(),
    )
  }

  @ReactMethod
  fun login(token: String, promise: Promise) {
    if (!initialized) {
      promise.reject(null, "Zendesk instance not initialized")
      return
    }

    module.loginUser(
      token = token,
      successCallback = { user ->
        val data: WritableMap = Arguments.createMap()
        data.putString("id", user.id)
        data.putString("externalId", user.externalId)
        promise.resolve(data)
      },
      failureCallback = { error -> promise.reject(error) },
    )
  }

  @ReactMethod
  fun logout(promise: Promise) {
    if (!initialized) {
      promise.reject(null, "Zendesk instance not initialized")
      return
    }

    module.logoutUser(
      successCallback = { _ -> promise.resolve(null) },
      failureCallback = { error -> promise.reject(error) },
    )
  }

  @ReactMethod
  fun openMessagingView(promise: Promise) {
    if (!initialized) {
      promise.reject(null, "Zendesk instance not initialized")
    }

    module.showMessaging(reactContext, Intent.FLAG_ACTIVITY_NEW_TASK)

    promise.resolve(null)
  }

  @ReactMethod
  fun sendPageViewEvent(event: ReadableMap, promise: Promise) {
    if (!initialized) {
      promise.reject(null, "Zendesk instance not initialized")
    }

    val pageTitle = event.getString("pageTitle")
    val url = event.getString("pageTitle")
    if (pageTitle == null || url == null) {
      promise.reject(null, "invalid page view event")
      return
    }

    val pageView = PageView(url = url, pageTitle = pageTitle)
    module.sendPageViewEvent(
      pageView = pageView,
      successCallback = { _ -> promise.resolve(null) },
      failureCallback = { error -> promise.reject(error) },
    )
  }

  @ReactMethod
  fun updatePushNotificationToken(newToken: String) {
    module.updatePushNotificationToken(newToken)
  }

  @ReactMethod
  fun getUnreadMessageCount(promise: Promise) {
    if (!initialized) {
      promise.reject(null, "Zendesk instance not initialized")
    }

    promise.resolve(module.getUnreadMessageCount() ?: 0)
  }

  @ReactMethod
  fun handleNotification(remoteMessage: ReadableMap, promise: Promise) {
    try {
      val messageData = remoteMessage.toHashMap().toMap() as Map<String, String>
      module.handleNotification(
        context = reactContext,
        messageData = messageData,
      ) { responsibility -> promise.resolve(responsibility) }
    } catch (error: Exception) {
      promise.reject(error)
    }
  }

  companion object {
    const val NAME = "ZendeskMessaging"
  }
}
