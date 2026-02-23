# Analyse du front Angular et propositions d'amélioration

## Synthèse rapide

Le front repose déjà sur Angular 18 avec composants standalone et Angular Material, ce qui est une bonne base. Les axes d'amélioration prioritaires concernent surtout:

1. **la maintenabilité** (composants trop volumineux et logique métier mélangée au rendu),
2. **la robustesse RxJS** (abonnements non sécurisés, risques de fuite mémoire),
3. **l'UX/navigation** (route d'accueil absente, accessibilité perfectible),
4. **l'outillage qualité** (linting, tests unitaires ciblés, vérifications de build en CI).

---

## 1) Architecture & organisation

### 1.1 Découper les composants "god objects"
- `CalendarComponent` concentre énormément de responsabilités: initialisation, cache, calcul de présence, interactions cellule, suppression/création d'événements, export PDF.
- Recommandation: extraire en services/facades dédiés:
  - `CalendarFacadeService` (chargement + orchestration),
  - `CalendarCacheService` (span/presence cache),
  - `CalendarExportService` (PDF/images),
  - `CalendarDialogsService` (ouverture/fermeture des dialogs).

**Bénéfice**: code plus testable, plus lisible, et réduction des effets de bord.

### 1.2 Uniformiser les conventions
- Mélange de styles d'injection (`constructor`, `inject()`), noms de fichiers (`HolidayService.ts` en PascalCase côté fichier), et imports redondants.
- Recommandation: standardiser:
  - noms de fichiers en `kebab-case`,
  - un style d'injection unique par équipe,
  - suppression des imports inutilisés.

**Bénéfice**: onboarding plus rapide et dette technique réduite.

---

## 2) Données & RxJS

### 2.1 Sécuriser les subscriptions
- Exemple observé: abonnement dans `SprintListComponent` sans pattern de destruction explicite.
- Recommandation Angular 16+:
  - utiliser `takeUntilDestroyed()` + `DestroyRef`,
  - ou convertir en `signal`/`toSignal()` lorsque pertinent.

**Bénéfice**: pas de fuite mémoire lors des navigations répétées.

### 2.2 Réduire les `any` et renforcer le typage
- Dans `CalendarComponent`, des accès `(ev as any).idEvent` apparaissent.
- Recommandation:
  - définir un contrat `EventModel` strict,
  - normaliser l'objet dès la couche service (adapter DTO -> modèle applicatif).

**Bénéfice**: erreurs captées à la compilation, moins de bugs runtime.

### 2.3 Limiter la logique de normalisation dans les composants
- La correction de nom (`Assih Jean-Samuel` -> `Jean-Samuel`) est faite côté composant.
- Recommandation: déplacer cette logique dans un mapper de service.

**Bénéfice**: séparation claire entre rendu et transformation de données.

---

## 3) Routing, UX & accessibilité

### 3.1 Ajouter une route d'accueil et une route wildcard
- La route racine est commentée et il n'y a pas de fallback `**`.
- Recommandation:
  - restaurer `path: ''` vers `HomeComponent`,
  - ajouter `path: '**'` vers une page 404 ou redirection.

**Bénéfice**: meilleure résilience de navigation et expérience utilisateur.

### 3.2 Supprimer les styles inline
- Plusieurs blocs HTML utilisent des `style="..."` directement.
- Recommandation: déplacer dans les `.css/.scss` composants et créer des classes utilitaires.

**Bénéfice**: cohérence visuelle, surcharge HTML réduite, meilleur theming.

### 3.3 Améliorer l'accessibilité Material
- Recommandation:
  - ajouter des `aria-label` explicites sur boutons icône,
  - vérifier le contraste mode sombre,
  - ajouter des états focus visibles.

**Bénéfice**: conformité a11y et meilleure ergonomie clavier/lecteur d'écran.

---

## 4) Performance

### 4.1 Généraliser `trackBy` dans les listes répétées
- Pour les `*ngFor` volumineux (calendrier/ressources), définir des fonctions `trackBy` stables.

### 4.2 Migrer progressivement vers Signals
- Pour les états UI locaux (mois courant, filtres, toggles), les signals réduisent le boilerplate RxJS.

### 4.3 Précharger/lazy-load les pages coûteuses
- Les vues lourdes (calendrier, burnup) bénéficieraient d'un lazy loading systématique.

**Bénéfice global**: moins de rerenders et meilleure réactivité perçue.

---

## 5) Qualité & outillage

### 5.1 Ajouter lint + format en scripts npm
- Le `package.json` n'expose que `start/build/watch/test`.
- Recommandation:
  - ajouter `lint` (ESLint Angular),
  - ajouter `format` (Prettier),
  - intégrer ces checks en CI.

### 5.2 Renforcer les tests unitaires ciblés
Priorités de tests:
- fonctions de calcul du calendrier (jours ouvrés, demi-journées, congés),
- navigation/routing (`openCapacityView`, routes invalides),
- composants UI critiques (tableau sprint + interactions).

### 5.3 Ajouter des garde-fous TypeScript stricts
- Activer/renforcer progressivement:
  - `strict`,
  - `noImplicitOverride`,
  - `noUncheckedIndexedAccess`.

**Bénéfice**: meilleure fiabilité long terme.

---

## Plan d'action recommandé (ordre)

1. **Semaine 1**: routing racine/wildcard + suppression styles inline les plus visibles + `aria-label`.
2. **Semaine 2**: sécurisation RxJS (`takeUntilDestroyed`) et nettoyage `any` critiques.
3. **Semaine 3**: extraction de la logique métier du calendrier dans une facade/service.
4. **Semaine 4**: lint/format/CI + lot de tests unitaires sur fonctions pures.

Ce plan permet d'obtenir rapidement des gains UX/qualité tout en préparant une refonte technique progressive sans rupture.
