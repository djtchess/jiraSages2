# Plan de tâches UX/UI – Thème clair/sombre et design moderne (Angular Front)

## 1) Fondations transverses (à faire en premier)

### 1.1 Audit UI/UX global
- [ ] Inventorier tous les écrans/composants visuels (home, sprint-list, sprint-scope, ticket-table, calendar, resources, charts, dialogs, spinner).
- [ ] Dresser un état des lieux des styles existants (`.css`/`.scss`) et identifier les duplications.
- [ ] Réaliser une matrice de contraste (texte, boutons, tableaux, bords, placeholders, graphiques) pour thème clair et sombre.

### 1.2 Design tokens + architecture de thème
- [ ] Définir un socle de design tokens (couleurs, typo, spacing, radius, ombres, z-index, durées d’animation).
- [ ] Créer des variables CSS globales dans `styles.scss` :
  - [ ] Couleurs sémantiques (`--color-bg`, `--color-surface`, `--color-text`, `--color-primary`, `--color-border`, `--color-danger`, etc.).
  - [ ] Variantes claires/sombres via `[data-theme='light']` / `[data-theme='dark']`.
- [ ] Introduire une convention de nommage SCSS (BEM ou utilitaires) pour réduire les conflits.
- [ ] Définir une grille responsive (breakpoints cohérents desktop/tablette/mobile).

### 1.3 Gestion du thème (fonctionnel)
- [ ] Créer un `ThemeService` Angular :
  - [ ] Lecture/écriture de préférence (`localStorage`).
  - [ ] Initialisation au démarrage selon préférence système (`prefers-color-scheme`).
  - [ ] API `toggleTheme()` + `setTheme(light|dark|system)`.
- [ ] Ajouter un switch global dans le layout principal (header/navigation).
- [ ] Persister le thème entre sessions et synchroniser sur tous les écrans.

### 1.4 Accessibilité & qualité UX
- [ ] Cibler WCAG AA minimum (contraste, focus visible, états hover/active/disabled).
- [ ] Uniformiser les tailles interactives (min 44px pour zones cliquables).
- [ ] Ajouter des transitions légères et cohérentes (150–250ms, easings fluides).
- [ ] Valider clavier + lecteurs d’écran pour composants critiques.

---

## 2) Modernisation visuelle par type de composant

### 2.1 Boutons (style futuriste mais sobre)
- [ ] Définir des variantes : primaire, secondaire, ghost, danger, icon-only.
- [ ] Ajouter des états visuels nets : hover, focus, active, disabled, loading.
- [ ] Appliquer radius moderne, ombres douces, gradients subtils (sans perte de lisibilité).
- [ ] Harmoniser icônes + alignement texte + feedback tactile.

### 2.2 Tableaux (lisibilité + densité maîtrisée)
- [ ] Créer un style unifié pour en-têtes, lignes, séparateurs, zebra-stripes.
- [ ] Optimiser contraste des cellules et alignement des colonnes numériques.
- [ ] Ajouter état hover de ligne + état sélection.
- [ ] Uniformiser pagination, tri, filtres et badges de statut.

### 2.3 Zones de recherche / filtres
- [ ] Standardiser les champs : hauteur, bordure, background, placeholder, icône, focus ring.
- [ ] Ajouter comportement clair pour erreurs/validation.
- [ ] Optimiser lisibilité des dropdowns/autocomplete en dark mode.
- [ ] Clarifier hiérarchie “recherche principale” vs “filtres avancés”.

---

## 3) Lisibilité des graphiques (clair/sombre)

### 3.1 Palette et contrastes data-viz
- [ ] Définir une palette dédiée graphiques pour chaque thème (catégories, séries, alerte/succès).
- [ ] Garantir contraste suffisant entre séries et fond (éviter teintes trop proches).
- [ ] Prévoir alternatives daltonisme-friendly.

### 3.2 Éléments du chart
- [ ] Adapter couleur des axes, grilles, labels, légendes et tooltips selon le thème.
- [ ] Uniformiser taille/poids des polices dans les charts.
- [ ] Vérifier lisibilité des points/lignes/barres sur petits écrans.
- [ ] Gérer états vides/chargement/erreur avec placeholders cohérents.

### 3.3 Validation fonctionnelle chart
- [ ] Tester chaque graphique en clair/sombre avec données faibles, moyennes et denses.
- [ ] Vérifier impressions/export captures si nécessaire (contraste préservé).
- [ ] Mettre en place snapshots visuels (tests de non-régression UI si possible).

---

## 4) Plan par écran (checklist de delivery)

### 4.1 `home`
- [ ] Revoir hiérarchie visuelle globale (titres, KPI, cartes, CTA).
- [ ] Harmoniser spacing et sections pour un rendu premium.
- [ ] Valider contraste clair/sombre sur blocs principaux.

### 4.2 `sprint-list`
- [ ] Uniformiser liste/cartes des sprints (header, métadonnées, actions).
- [ ] Repenser filtres/recherche et états de survol.
- [ ] Ajouter feedback visuel sur sélection et navigation.

### 4.3 `sprint-scope`
- [ ] Clarifier la hiérarchie des informations clés (scope, progression, alertes).
- [ ] Améliorer composants de synthèse (cards, badges, tags).
- [ ] Vérifier cohérence des couleurs statut en clair/sombre.

### 4.4 `ticket-table`
- [ ] Refondre le style tableau (en-tête sticky optionnel, densité lisible, badges statut).
- [ ] Standardiser interactions tri/filtre/recherche.
- [ ] Optimiser l’ergonomie mobile/tablette (scroll horizontal guidé si nécessaire).

### 4.5 `calendar`
- [ ] Adapter styles des événements, cellules et dialogues aux deux thèmes.
- [ ] Renforcer contraste des événements et légendes.
- [ ] Vérifier lisibilité des états sélection/survol/jour courant.

### 4.6 `resources`
- [ ] Harmoniser cartes, indicateurs de capacité et statuts.
- [ ] Revoir boutons d’action + filtres pour cohérence globale.
- [ ] Tester lisibilité dense (plusieurs ressources/projets).

### 4.7 Dialogs, spinner, composants transverses
- [ ] Uniformiser les modales (fond, ombre, overlay, typographie, CTA).
- [ ] Moderniser spinner/skeleton avec thème adaptatif.
- [ ] Vérifier toutes les micro-interactions (toasts, tooltips, menus).

---

## 5) Qualité, tests et industrialisation

### 5.1 Tests UI/UX
- [ ] Créer une checklist QA visuelle claire/sombre par écran.
- [ ] Ajouter tests e2e clés (changement thème, persistance, composants critiques).
- [ ] Ajouter tests de contraste automatisés (axe/lighthouse si possible).

### 5.2 Documentation & gouvernance
- [ ] Documenter la charte UI (tokens, composants, règles d’usage).
- [ ] Fournir un guide “Do/Don’t” pour maintenir le style moderne dans le temps.
- [ ] Définir une Definition of Done UX (thème, accessibilité, responsive, états).

### 5.3 Roadmap de livraison (suggestion)
- [ ] Sprint 1 : fondations thème + tokens + boutons/champs globaux.
- [ ] Sprint 2 : tableaux + listes + dialogues + composants transverses.
- [ ] Sprint 3 : graphiques + finitions écran par écran + QA complète.
