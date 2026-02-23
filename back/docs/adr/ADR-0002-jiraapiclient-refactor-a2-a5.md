# ADR-0002 — Implémentation du refactor `JiraApiClient` (A2 à A5)

- **Statut**: Accepted
- **Date**: 2026-02-23
- **Contexte**: Suite de l'ADR-0001 (cartographie A1).

## Décisions

1. `JiraApiClient` reste la façade publique mais délègue désormais:
   - le parsing JSON Jira à `JiraParser`,
   - la gestion de cache changelog à `ChangelogCacheService`,
   - l'exécution HTTP à `JiraHttpClient`.

2. Les signatures publiques de `JiraApiClient` sont conservées pour éviter les régressions côté contrôleurs/services.

3. Le cache changelog est encapsulé dans un service dédié (TTL 2h, éviction par clé).

## Portée technique réalisée

- **A2 (`JiraHttpClient`)**: `JiraApiClient` continue d'utiliser exclusivement `JiraHttpClient` pour les appels HTTP Jira.
- **A3 (`JiraParser`)**: extraction du parsing de tickets, changelog, boards/sprints, dates Jira.
- **A4 (`ChangelogCacheService`)**: extraction du cache TTL + invalidation.
- **A5 (façade orchestratrice)**: simplification de `JiraApiClient` (moins de parsing brut, moins de logique cache en ligne).

## Risques et limites

- Le comportement métier est conservé volontairement (pas de changement de règles burnup/KPI dans ce lot).
- La stratégie cache reste in-memory locale (non distribuée), comme avant.
