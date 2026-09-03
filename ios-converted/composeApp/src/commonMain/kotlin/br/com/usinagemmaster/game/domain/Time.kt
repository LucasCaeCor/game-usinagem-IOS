package br.com.usinagemmaster.game.domain

/**
 * Contrato multiplataforma para operações de tempo usadas pelas regras do jogo.
 *
 * Mantemos somente primitivas (Long/Int) no commonMain para não acoplar
 * o domínio a java.util.Calendar, Foundation ou outra API de plataforma.
 */
expect fun currentTimeMillis(): Long

/**
 * Retorna a hora local (0..23) correspondente ao instante Unix em milissegundos.
 */
expect fun currentHourOfDay(now: Long): Int
