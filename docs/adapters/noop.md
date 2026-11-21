# Adapter `noop` — mode standalone

## Positionnement

JokkoRM tourne **sans maître**. Les lectures sortantes (`fetchByCote`, `resolveAgent`) retournent `Optional.empty()`. Les notifications sortantes (`notifyPhysicalLocation`, `notifyMovement`, `notifyDestructionExecuted`) sont loguées en INFO et jetées.

C'est le mode **par défaut** (`jokkorm.master.kind=noop` dans `application.yml`), activé automatiquement si aucun autre adapter n'est explicitement sélectionné.

## Cas d'usage

- **Pilote / POC** : montrer le flux physique à un prospect sans brancher un vrai Maarch.
- **Démo** : `rm.jokkofile.com` (à venir) tourne en mode noop pour la navigation publique.
- **Déploiement captif** : organisation qui n'a pas (ou pas encore) de GED numérique et veut d'abord structurer son magasin papier.
- **Tests** : simplifie les IT, pas besoin de WireMock d'un faux maître.

## Activation

Dans `application.yml` (ou variable d'env `JOKKORM_MASTER_KIND`) :

```yaml
jokkorm:
  master:
    kind: noop  # valeur par défaut si la propriété n'est pas définie
```

## Flux concrets

### Côté API publique

- `POST /api/v1/events/master/cote-assigned` reste utilisable : un opérateur (ou un script) peut déclencher la provision de la cote à la main, par exemple via curl après la clôture manuelle d'un dossier.
- `POST /api/v1/events/master/legal-hold` idem — gel/dégel piloté depuis un script ou un futur écran admin.

En mode noop, ces événements ne viennent pas d'un hook HMAC : ils viennent d'un utilisateur ou d'un script interne. Sécurité : OIDC Bearer comme le reste de l'API.

### Côté notifications sortantes

Quand JokkoRM a quelque chose à remonter au maître (attribution d'emplacement, mouvement, destruction), l'adapter noop log :

```
INFO  [noop] notifyPhysicalLocation tenant=acme cote=DMG/MP/2024/047 location=Diamniadio / MAG-B / 14 / 3 / 07
INFO  [noop] notifyMovement tenant=acme cote=DMG/MP/2024/047 kind=LOAN_OUT agent=khady.diouf at=2026-11-21T10:00:00Z
INFO  [noop] notifyDestructionExecuted tenant=acme cote=DMG/MP/2024/047 attestation=uuid (pdf 48234 bytes)
```

L'information n'est pas perdue : elle est **tracée en logs structurés JSON** (phase 2+, via Logstash encoder), donc exploitable par Loki / Elasticsearch / CloudWatch. Pour la rejouer plus tard vers un vrai maître, voir « Migration vers un vrai maître » ci-dessous.

## Limites assumées

- **Aucune vérité maître** sur les cotes : si l'opérateur poste une cote qui n'existe pas côté maître (il n'y a pas de maître en noop), c'est OK — JokkoRM accepte toute cote qui respecte le charset.
- **Pas de gel automatique** : aucun hook ne gèlera tout seul une archive sous contentieux — un humain (ou un script) doit poster `POST /legal-hold`.
- **Pas de recherche fédérée** : l'endpoint `/records/{cote}/consolidated` (phase 3) retournera `master.status = disabled` en mode noop, les pièces numériques seront simplement absentes de la vue.

## Migration vers un vrai maître

Changer `jokkorm.master.kind` de `noop` à `maarch` (ou `generic`) et redémarrer. Les enveloppes déjà créées en noop restent valides — la cote est préservée, le maître la retrouvera via son propre référentiel.

Pour rejouer vers le maître les notifications sortantes émises en mode noop, un script phase ultérieure parsera les logs JSON et relancera les appels sortants. Non prioritaire tant qu'aucun client n'a besoin de cette migration à froid.

## Tests

Couvert par les IT phase 1 et phase 2 : pas d'IT dédié à noop en tant que tel, puisqu'il est l'implémentation utilisée par défaut dans tous les IT. Bug noop = bug IT.
