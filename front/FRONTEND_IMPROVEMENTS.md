# Audit expert Angular (front) — robustesse & maintenabilité

## Objectif
Structurer un plan d’action concret pour rendre le front plus **robuste**, **maintenable** et **prévisible** en production.

---

## 1) Priorité P0 (à corriger immédiatement)

### P0.1 — Corriger l’accumulation de jours fériés (bug fonctionnel)
**Constat**
- `HolidayService.getHolidays()` appelle `calculateHolidays()` qui pousse (`push`) dans un tableau partagé `this.holidays` sans reset.
- Résultat: appels successifs = doublons croissants et calculs de planning faussés.

**Actions**
1. Rendre `calculateHolidays(year)` pure: retourner un nouveau tableau plutôt que muter un état global.
2. Mettre en place un cache par année (`Map<number, Holiday[]>`) immuable.
3. Ajouter tests unitaires: 2 appels consécutifs sur la même année ne doivent pas augmenter la taille.

**Impact**
- Fiabilité fonctionnelle du calendrier.
- Élimination d’un bug silencieux difficile à diagnostiquer.

### P0.2 — Ajouter une vraie route racine + wildcard
**Constat**
- La route `''` est commentée et aucune route `**` n’existe.
- Le menu propose pourtant un lien vers `/`.

**Actions**
1. Réactiver la route d’accueil (`HomeComponent` ou redirection explicite).
2. Ajouter `path: '**'` vers une page 404 standalone.
3. Couvrir par tests de routing (URL invalide => 404/redirection).

**Impact**
- Navigation robuste.
- Meilleure UX au premier chargement et sur URL profondes.

### P0.3 — Supprimer les abonnements "non bornés" dans les composants
**Constat**
- `AppComponent` s’abonne à `activeTheme$` sans stratégie de teardown.
- `SprintListComponent` s’abonne directement aux sprints sans gestion de cycle de vie.

**Actions**
1. Standardiser avec `takeUntilDestroyed(inject(DestroyRef))`.
2. Alternative recommandée Angular 17/18: convertir en `signal` via `toSignal()` quand pertinent.
3. Ajouter règle d’équipe: pas de `subscribe()` en composant sans stratégie de destruction.

**Impact**
- Réduction des fuites mémoire et effets de bord après navigation.

---

## 2) Priorité P1 (refactoring structurant)

### P1.1 — Réduire la complexité de `CalendarComponent`
**Constat**
- Le composant concentre: chargement data, normalisation, cache, logique métier, CRUD événements, export PDF, interactions UI.

**Actions**
1. Créer une façade (`CalendarFacadeService`) pour orchestrer chargement + mutations.
2. Extraire le cache (`CalendarCacheService`) et la logique d’export (`CalendarExportService`).
3. Limiter le composant à l’état d’affichage + handlers UI.

**Impact**
- Forte amélioration testabilité/évolutivité.
- Diminution du risque de régression lors des changements métier.

### P1.2 — Éliminer les `any` et normaliser les DTO
**Constat**
- Le calendrier utilise `(ev as any).idEvent` / `(ev as any).id`.

**Actions**
1. Définir un contrat frontend unique (`CalendarEvent` avec id obligatoire).
2. Ajouter un mapper HTTP côté service pour convertir DTO backend -> modèle strict.
3. Interdire l’usage de `any` dans ce flux critique.

**Impact**
- Erreurs détectées à la compilation.
- Contrats API plus stables et explicites.

### P1.3 — Centraliser la configuration API
**Constat**
- `JiraService` et `SprintService` hardcodent `http://localhost:8088/api`.

**Actions**
1. Basculer vers `environment.ts` / `environment.prod.ts`.
2. Utiliser un token d’injection (`API_BASE_URL`) partagé.
3. Ajouter un `HttpInterceptor` pour erreurs globales et éventuellement auth.

**Impact**
- Déploiement multi-environnements sécurisé.
- Réduction des erreurs de configuration.

---

## 3) Priorité P2 (qualité technique et DX)

### P2.1 — Nettoyer l’initialisation Angular
**Constat**
- `provideHttpClient()` et animations sont déclarés plusieurs fois (`main.ts` + `app.config.ts`).

**Actions**
1. Définir une source unique de providers (recommandé: `app.config.ts`).
2. Garder `bootstrapApplication(AppComponent, appConfig)` sans duplication.
3. Vérifier startup via test smoke.

### P2.2 — Conventions et cohérence projet
**Constat**
- Nommage de fichier atypique (`HolidayService.ts`), mélange des styles DI, code commenté conservé dans des fichiers cœur.

**Actions**
1. Uniformiser naming (`kebab-case` fichiers).
2. Définir conventions DI (constructor ou `inject`) et les appliquer.
3. Supprimer le code mort/commenté de production.

### P2.3 — Outillage qualité
**Constat**
- Scripts NPM limités (`start`, `build`, `test`), pas de `lint`/`format`.

**Actions**
1. Ajouter ESLint Angular + Prettier.
2. Ajouter scripts `lint`, `format`, `format:check`.
3. Exécuter `lint + test + build` en CI à chaque PR.

---

## 4) Plan d’exécution recommandé (4 semaines)

### Semaine 1 — Stabilisation production (P0)
- Fix `HolidayService` + tests.
- Routing racine + wildcard + test routing.
- Sécurisation subscriptions (`AppComponent`, `SprintListComponent`).

### Semaine 2 — Contrats et couche data (P1)
- Modèle `CalendarEvent` strict.
- Mapper DTO dans services.
- Externalisation `API_BASE_URL` + environnements.

### Semaine 3 — Refactoring calendrier (P1)
- Extraction façade/cache/export.
- Simplification du composant et des handlers.

### Semaine 4 — Qualité et industrialisation (P2)
- ESLint/Prettier.
- Pipeline CI qualité.
- Nettoyage conventions + dette technique restante.

---

## 5) KPI de succès
- 0 fuite mémoire détectée sur navigation répétée.
- 0 duplication des jours fériés sur multi-chargements.
- Couverture unitaire des services critiques (holiday/calendar/routing).
- Pipeline CI vert avec `lint + test + build`.
