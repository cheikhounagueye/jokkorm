# JokkoRM — Sécurité

## Signaler une vulnérabilité

**Ne pas ouvrir d'issue publique.** Envoyer un mail chiffré à `security@jokkofile.com` (clé PGP à publier en v0.1.0). Réponse sous 72h, correctif coordonné sous 30 jours, disclosure publique après patch.

## Modèle de menace (vue d'ensemble)

Les actifs sensibles détenus par JokkoRM :

1. **Topographie physique** — où est chaque boîte. Révélation = carte au trésor pour un attaquant physique.
2. **Journaux d'audit HMAC** — chaîne liant chaque geste à un agent. Altération = perte de valeur probante.
3. **Secrets d'adapter maître** — tokens API Maarch, secrets webhook HMAC. Révélation = usurpation croisée.
4. **Données tenant** — row-level isolation ; une fuite cross-tenant est le pire scénario produit.

## Contrôles principaux

- **Auth** : OIDC obligatoire (Keycloak), aucune route API ouverte sauf `/actuator/health` et `/actuator/info`.
- **Multi-tenant** : ADR-0002 ; tous les ports portent `TenantId`, un test dédié « cross-tenant leak » en IT dès qu'un bounded context est écrit.
- **Audit chain HMAC** : à importer de JokkoFile en phase 1, chaîné par tenant.
- **Secrets** : jamais dans le repo. Rotation documentée pour chaque secret externe.
- **CORS** : par défaut fermé. L'origine frontend est whitelistée par tenant.
- **Rate limiting** : Spring Cloud Gateway ou filtre custom (phase 2), par tenant + par client.

## Standards visés

- ISO 27001 — A.5.15 (contrôle d'accès), A.8.15 (journalisation), A.5.34 (confidentialité)
- NF Z 42-013 (où applicable — JokkoRM est module physique, pas le SAE lui-même ; le maître porte la conformité SAE)
- RGPD — données personnelles minimales (agent name, timestamps), base légale = obligation légale d'archivage pour les administrations clientes
- CNIL délibération 2024-01 — quorum ≥ 2 pour destruction RH (déjà implémenté côté JokkoFile, repris ici)

## Dépendances tierces critiques

À auditer trimestriellement :
- Spring Boot (CVE tracking automatisé via Dependabot)
- Keycloak (version supportée uniquement)
- PostgreSQL JDBC

## Supply chain

- Signature des releases avec cosign (à partir de v0.1.0).
- SBOM CycloneDX généré à chaque release (à partir de v0.1.0).
- Base image Docker : `eclipse-temurin:21-jre-alpine` (minimal, maintenue).
