# Analyse backend (JDK 21) et plan d'amélioration pas à pas — mise à jour

## Synthèse (état actuel)

Le backend a bien progressé depuis la première analyse:

- ✅ **Étape 2 avancée**: validation d'entrée et contrat d'erreur homogène via `GlobalExceptionHandler` + exceptions métier.
- ✅ **Étape 3 avancée**: logique d'analyse sprint extraite vers `JiraSprintAnalysisService`.
- ✅ **Étape 4 avancée**: `pom.xml` simplifié, aligné Spring Boot BOM, avec `maven-enforcer-plugin`.

Les chantiers les plus prioritaires restant à traiter:

1. 🔴 **Sécurité configuration (Étape 1 non faite)**: secrets Jira/DB toujours versionnés.
2. 🟠 **Observabilité & robustesse runtime**: logs `System.out`, proxy codé en dur, peu de métriques.
3. 🟠 **Testabilité (Étape 5 partielle)**: quasi absence de tests unitaires / web / intégration.
4. 🟡 **Découpe JiraApiClient à finaliser**: la classe reste volumineuse, surtout sur le burnup.

---

## État détaillé des étapes

## Étape 1 — Sécuriser la configuration

### Statut
❌ **À faire en priorité**.

### Constat
- `application.properties` contient encore:
  - `spring.datasource.username/password`
  - `jira.username`
  - `jira.apiToken`

### Actions recommandées
- Remplacer les valeurs sensibles par variables d'environnement (avec fallback vide).
- Ajouter `application-example.properties` documenté.
- Révoquer/renouveler le token Jira exposé.
- Ajouter une règle de scan secret en CI (ex: gitleaks/trufflehog).

### Critères de validation
- Aucun secret actif en clair dans le repo.
- Démarrage local et CI via variables d'environnement.

---

## Étape 2 — Stabiliser le contrat REST

### Statut
✅ **Majoritairement fait**.

### Déjà en place
- Gestion d'erreur structurée (`ApiErrorResponse`).
- `GlobalExceptionHandler` avec 400/404/502/500.
- Validation de paramètres côté contrôleurs (`@Validated`, contraintes).

### Améliorations restantes
- Ajouter des codes métier stables (`errorCode`) dans la réponse d'erreur.
- Uniformiser le message des erreurs de validation pour le front.
- Ajouter tests `@WebMvcTest` dédiés au handler et aux endpoints sensibles.

---

## Étape 3 — Finaliser la découpe de JiraApiClient

### Statut
🟡 **Bien avancé, à terminer**.

### Déjà en place
- Extraction de l'analyse sprint dans `JiraSprintAnalysisService`.

### Travaux restants (proposés)
- Extraire la logique burnup de `JiraApiClient` vers un service dédié (ex: `JiraBurnupService`).
- Remplacer les `System.out.println` par `Slf4j`.
- Réduire les `throws Exception` sur la façade Jira au profit d'exceptions typées.
- Isoler encore la logique de pagination Jira (helper dédié).

### Critères de validation
- `JiraApiClient` ramené à un rôle de façade/orchestrateur.
- Méthodes longues (>80 lignes) découpées en services testables.

---

## Étape 4 — Moderniser le build Maven JDK 21

### Statut
✅ **Fait sur le périmètre demandé**.

### Déjà en place
- Dépendances nettoyées et alignées BOM Spring Boot.
- Retrait des libs historiques/commentées.
- `maven-enforcer-plugin` (Java 21 + convergence dépendances).

### Améliorations complémentaires
- Ajouter `maven-surefire-plugin` + `maven-failsafe-plugin` explicitement pour bien séparer unit/integration tests.
- Ajouter JaCoCo avec seuil minimal progressif.

---

## Étape 5 — Tests & observabilité

### Statut
🟠 **Faible couverture actuelle**.

### Constat
- Test de contexte uniquement (`AgileApplicationTests`).

### Plan proposé
- **Lot 5.1 (rapide)**: tests unitaires `JiraSprintAnalysisService`.
- **Lot 5.2**: `@WebMvcTest` pour `GlobalExceptionHandler` et `SprintController`.
- **Lot 5.3**: tests intégration repository (base de test / Testcontainers).
- **Lot 5.4**: Actuator + Micrometer (latence appels Jira, taux d'erreurs).

### Critères de validation
- CI exécutant unit + web + integration tests.
- Couverture minimale mesurée et suivie.

---

## Plan d’exécution recommandé (prochaines itérations)

### Itération A (sécurité immédiate, 1-2 jours)
1. Nettoyage secrets + `application-example.properties`.
2. Rotation token Jira.
3. PR de sécurisation + checklist CI.

### Itération B (qualité runtime, 2-3 jours)
1. Remplacement `System.out` -> logs structurés.
2. Externaliser la config proxy Jira.
3. Exception mapping Jira plus fin.

### Itération C (testabilité, 3-5 jours)
1. Tests unitaires `JiraSprintAnalysisService`.
2. Tests web de contrat d'erreur.
3. JaCoCo + seuil initial.

### Itération D (architecture finale Jira, 3-5 jours)
1. Extraction `JiraBurnupService`.
2. Façade `JiraApiClient` simplifiée.
3. Non-régression via tests ciblés.

---

## Recommandation pour démarrer tout de suite

➡️ **Commencer par l’Itération A (sécurité)**: c’est le meilleur ratio risque/effort et cela réduit immédiatement l’exposition opérationnelle.
