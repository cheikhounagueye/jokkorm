/**
 * Bounded context <em>physical</em> — topographie des locaux, conteneurs, mouvements.
 *
 * <p>C'est le cœur de l'archivage physique : le pattern 2 gardiens dit que ces données
 * sont <b>détenues par JokkoRM</b> (ligne « Topographie / Contenants / Mouvements » du
 * tableau de répartition). Rien de tout ça ne vit chez le maître — JokkoRM est source.
 *
 * <p>Pas de dépendance framework ici.
 */
package com.jokkorm.domain.physical;
