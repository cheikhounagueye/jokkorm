# Pattern 11 — Droits : le contenu chez le maître, les gestes chez JokkoRM

**Statut** : validé le 2026-11-23 · **Complète** : patterns 02, 07, 08 · **Mise en œuvre** :
tranche 2 de l'interconnexion Maarch RM

## 1. Le problème

Une boîte d'archives a deux gardiens (pattern 01). Qui décide qu'Awa (DRH) peut emprunter la
boîte B12 ? Si l'on recopie les droits de la GED dans JokkoRM, on obtient deux listes qui
divergent : une personne retirée dans Maarch garderait son accès dans JokkoRM.

## 2. La règle

| Qui décide | De quoi | Exemples |
|---|---|---|
| **La GED maître** (Maarch RM…) | qui peut **savoir et consulter** le contenu d'une archive | voir qu'un dossier existe, son titre, sa communicabilité, le numérique |
| **JokkoRM** | qui peut faire un **geste physique** | ranger, déplacer, prêter, approuver une sortie en 4 yeux, gérer la salle |

**Un geste qui donne accès au contenu (prêt, extraction, numérisation, affichage du libellé)
exige les deux autorisations.** Le plus restrictif l'emporte. Si le maître ne répond pas, la
réponse est non.

Les droits ne sont jamais recopiés : JokkoRM interroge le maître au moment où il en a besoin
(cache de quelques minutes au plus) et trace chaque décision dans son journal d'audit.

## 3. Exemples

- Awa (DRH) demande une boîte de dossiers du personnel. Le maître : elle peut les voir.
  JokkoRM : elle peut faire une demande. **Accordé.**
- Moussa (Finances) demande la même boîte. Le maître : non. **Refusé**, même si JokkoRM
  l'autorise à faire des demandes.
- Une boîte mélange des dossiers DRH et Finances. Awa ne peut pas emprunter la boîte entière
  (elle verrait les dossiers Finances) : **extraction de ses pièces seulement**.

## 4. Conditions

1. **Une seule identité par personne** pour les deux logiciels, via l'annuaire de
   l'organisation (SSO). Sans elle, impossible de rapprocher « awa.diop » et « adiop ».
2. **Le maître est interrogé au nom de la personne**, jamais avec un compte technique qui
   voit tout. (En test, l'espace ACME lit Maarch avec un seul compte : acceptable pour tester,
   pas chez un client.)
3. **Les rôles physiques** (demandeur, archiviste, responsable des archives, auditeur) sont
   administrés dans JokkoRM ; les droits sur le contenu, dans le maître.
