@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package br.com.usinagemmaster.game.persistence

import platform.Foundation.NSUserDefaults

actual object PlatformSaveStorage {
    private val defaults: NSUserDefaults
        get() = NSUserDefaults.standardUserDefaults

    actual fun read(key: String): String? = defaults.stringForKey(key)

    actual fun write(key: String, value: String) {
        defaults.setObject(value, forKey = key)
    }

    actual fun remove(key: String) {
        defaults.removeObjectForKey(key)
    }
}
