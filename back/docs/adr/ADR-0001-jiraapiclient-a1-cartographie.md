# ADR-0001 — Cartographie des responsabilités de `JiraApiClient` (A1)

- **Statut**: Accepted
- **Date**: 2026-02-23
- **Contexte**: Epic A — refactor de `JiraApiClient` en façade orchestratrice.

## 1) Inventaire des responsabilités actuelles

`JiraApiClient` mélange aujourd'hui plusieurs couches techniques et métier.

### 1.1 HTTP Jira (construction/exécution)
- Appels sortants vers Jira via `JiraHttpClient` depuis :
  - `getTicketsParJql(...)`
  - `getChangelogData(...)`
  - `getSprintInfo(...)`
  - `getBoardsForProject(...)`
  - `getAllSprintsForBoard(...)`

### 1.2 Parsing / mapping JSON
- Parsing JSON de réponses Jira (`ObjectMapper`, `JsonNode`) dans :
  - `getTicketsParJql(...)`
  - `getChangelogData(...)`
  - `getSprintInfo(...)`
  - `getBoardsForProject(...)`
  - `getAllSprintsForBoard(...)`
- Parsing/normalisation de dates Jira :
  - `parseJiraDate(...)`
  - `resolveEffectiveEndDate(...)`

### 1.3 Pagination
- Pagination Jira Search JQL (`nextPageToken`, `isLast`) dans `getTicketsParJql(...)`.
- Pagination Jira Changelog (`startAt`, `maxResults`, `total`) dans `getChangelogData(...)`.
- Pagination boards (`startAt`, `isLast`) dans `getBoardsForProject(...)`.
- Pagination sprints (`startAt`, `isLast`) dans `getAllSprintsForBoard(...)`.

### 1.4 Cache
- Cache local des changelogs : `changelogCache` + `changelogCacheTs`.
- Politique TTL en mémoire : `CHANGELOG_TTL = 2h`.
- API cache : `getChangelogCached(...)`, `invalidateChangelog(...)`.

### 1.5 Logique métier (domain Jira + KPI sprint)
- Détection « dev terminé avant sprint » :
  - `isDevTermineAvantSprintFromData(...)`
  - `isDevTermineAvantSprint(...)`
- Burnup et capacité :
  - `calculateBurnupForSprint(...)`
  - `calculateAvancementAvantSprint(...)`
  - `accumulateDailyDone(...)`
  - `calculateCapacity(...)`
  - `assembleBurnupPoints(...)`
  - `computeTotals(...)`
- Analyse d'engagement sprint :
  - `analyseTicketsSprint(...)`
  - `collectTicketsForSprintAnalysis(...)`
  - `classifyTicketsByChangelog(...)`
  - `fallbackAddOrCommit(...)`
- Génération JQL et règles de filtrage :
  - `getTicketBetweenDateSprint(...)`
  - `getTicketsSprint(...)`
  - `containsSprint(...)`
  - constantes métier (`LISTE_TYPES`, `LISTE_STATUTS_COMPLETS`, `LISTE_COMPTES`, ...).

## 2) Dépendances externes identifiées

### 2.1 Dépendances techniques
- `ObjectMapper` statique dans `JiraApiClient` pour sérialisation/désérialisation JSON.
- `JiraHttpClient` (wrapper `HttpClient`) pour tous les appels Jira.
- `HttpClient` Java 11 dans `JiraHttpClient` avec :
  - timeout de connexion,
  - proxy codé en dur `proxy-web.cnamts.fr:3128`,
  - stratégie retry rudimentaire sur 429/5xx.

### 2.2 Dépendances configuration
- Propriétés Jira injectées :
  - `jira.baseUrl`
  - `jira.username`
  - `jira.apiToken`

### 2.3 Dépendances métier internes (Spring)
- `SprintService`
- `DevelopperService`
- `EventService`
- `JoursFeriesService`
- `SprintCapacityCalculator`

## 3) Mini schéma de flux

```text
JiraController / SprintCapacityService
            |
            v
      JiraApiClient (façade actuelle)
   [orchestration + parsing + pagination
    + cache + logique métier]
            |
            v
        JiraHttpClient
            |
            v
         API Jira Cloud
```

## 4) Zones à risque (pré-refactor)

1. **Méthodes les plus exposées côté appelants**
   - `getSprintInfo(...)` est utilisée par plusieurs endpoints REST.
   - `calculateBurnupForSprint(...)` est directement exposée via endpoint `/burnup/{sprintId}`.
   - `getTicketsParJql(...)` est appelée dans le calcul de capacité sprint (plusieurs parcours).
   - `analyseTicketsSprint(...)` est utilisée par l'endpoint `full-info` (chaînage avec KPI).

2. **Risque cache changelog**
   - Cache uniquement in-memory (non distribué), sans limite de taille.
   - TTL fixe 2h : risque de données périmées sur tickets très actifs.
   - Invalidation manuelle (`invalidateChangelog`) peu appelée => risque d'écart temporel.

3. **Risque pagination / volumétrie**
   - Plusieurs boucles de pagination indépendantes ; toute divergence de contrat Jira peut casser silencieusement le comportement métier.

4. **Risque couplage fort**
   - Une seule classe concentre HTTP, parsing, cache et règles métier : testabilité et évolution difficiles.

## 5) Périmètre exact à extraire (cible Epic A)

- **Vers `JiraHttpClient`**: standardiser tous les patterns HTTP/rest, retry, proxy, timeouts, codes erreurs.
- **Vers `JiraParser`**: extraire les transformations `JsonNode -> DTO/domain` (issues, sprint, board, changelog, dates).
- **Vers `ChangelogCacheService`**: encapsuler TTL, invalidation et stratégie mémoire.
- **Conserver dans `JiraApiClient`**: orchestration des cas d'usage (enchaînements, règles de décision entre services).

