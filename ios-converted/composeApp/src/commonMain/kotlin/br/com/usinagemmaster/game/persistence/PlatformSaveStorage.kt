package br.com.usinagemmaster.game.persistence

expect object PlatformSaveStorage {
    fun read(key: String): String?
    fun write(key: String, value: String)
    fun remove(key: String)
}
