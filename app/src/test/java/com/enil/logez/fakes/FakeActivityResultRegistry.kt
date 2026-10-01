package com.enil.logez.fakes

import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat

/**
 * Stands in for Android's permission dialogs: records each request and answers it at once. A
 * multiple-permission request (an array) is answered with [locationAnswer], a single one with
 * [notificationAnswer]. Provide [owner] through LocalActivityResultRegistryOwner.
 */
internal class FakeActivityResultRegistry : ActivityResultRegistry() {
    val launched = mutableListOf<Any?>()
    var notificationAnswer = false
    var locationAnswer: Map<String, Boolean> = emptyMap()

    val owner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry get() = this@FakeActivityResultRegistry
    }

    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?,
    ) {
        val answer: Any = when (input) {
            is Array<*> -> {
                launched.addAll(input)
                locationAnswer
            }
            else -> {
                launched.add(input)
                notificationAnswer
            }
        }
        @Suppress("UNCHECKED_CAST")
        dispatchResult(requestCode, answer as O)
    }
}
