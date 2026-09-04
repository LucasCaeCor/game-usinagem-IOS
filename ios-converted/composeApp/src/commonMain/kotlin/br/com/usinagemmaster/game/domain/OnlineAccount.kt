package br.com.usinagemmaster.game.domain

/**
 * Ponte pequena para a camada nativa iOS.
 *
 * O domínio do jogo continua independente do Firebase.
 * A autenticação é uma responsabilidade de plataforma e não altera o save local.
 */
expect fun onlineAccountLabel(): String
expect fun openOnlineAccountPanel()
