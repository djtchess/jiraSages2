# Analyse backend (JDK 21) et plan d'amélioration pas à pas

## Constat rapide

Le backend Spring Boot est déjà structuré (controller/service/repository), mais il reste des points importants à améliorer pour fiabiliser et moderniser le projet en JDK 21:

1. **Sécurité/configuration**: secrets Jira et DB présents dans `application.properties`.
2. **Qualité d'API**: certains endpoints exposent encore `throws Exception` et la validation d'entrée est limitée.
3. **Architecture**: `JiraApiClient` reste une classe d'orchestration volumineuse malgré des extractions déjà initiées (`JiraHttpClient`, `JiraParser`, `ChangelogCacheService`).
4. **Build/outillage**: dépendances Maven historiques/redondantes et stratégie de tests encore légère.

---

## Plan d'exécution en étapes (itératif)

## Étape 1 — Sécuriser la configuration (priorité haute)

### Objectif
Ne plus versionner de secrets et rendre la configuration portable entre environnements.

### Actions
- Remplacer les credentials en clair par des variables d'environnement (`SPRING_DATASOURCE_*`, `JIRA_*`).
- Ajouter un fichier d'exemple documenté (`application-example.properties`) sans valeurs sensibles.
- Révoquer/renouveler immédiatement le token Jira exposé.

### Critères de validation
- Démarrage local via variables d'environnement.
- Aucun secret actif dans le dépôt Git.

---

## Étape 2 — Stabiliser le contrat REST

### Objectif
Obtenir des endpoints robustes, testables et prévisibles.

### Actions
- Remplacer `throws Exception` côté contrôleurs par des exceptions métier.
- Uniformiser `GlobalExceptionHandler` avec un format d'erreur stable (code, message, timestamp, path).
- Ajouter Bean Validation (`@Valid`, `@NotNull`, `@Positive`, bornes sur les pourcentages).

### Critères de validation
- Plus de `throws Exception` sur les endpoints publics.
- Réponses 400/404/500 homogènes et documentées.

---

## Étape 3 — Finaliser la découpe de `JiraApiClient`

### Objectif
Avoir une façade légère centrée sur l'orchestration.

### Actions
- Continuer l'extraction des règles métier résiduelles vers services dédiés (ex: statut/changelog, burnup).
- Réduire la taille des méthodes longues (pagination + filtrage + enrichissement).
- Ajouter des tests unitaires ciblés sur parser/cache/HTTP.

### Critères de validation
- Complexité cyclomatique réduite sur `JiraApiClient`.
- Couverture de tests accrue sur les composants Jira.

---

## Étape 4 — Moderniser le `pom.xml` pour JDK 21

### Objectif
Nettoyer les dépendances et aligner les versions sur Spring Boot 3.3.

### Actions
- Supprimer les dépendances redondantes/commentées.
- Retirer `junit-jupiter-engine` versionné manuellement (laisser le BOM Spring gérer).
- Vérifier la nécessité des starters non utilisés (`freemarker`, `mail`, etc.).
- Ajouter `maven-enforcer-plugin` (Java 21 + cohérence dépendances).

### Critères de validation
- `mvn test` vert après nettoyage.
- Arbre de dépendances simplifié et maintenable.

---

## Étape 5 — Renforcer tests et observabilité

### Objectif
Sécuriser les évolutions futures.

### Actions
- Ajouter tests unitaires service/parsing.
- Ajouter `@WebMvcTest` sur contrôleurs clés.
- Ajouter tests d'intégration (repositories + DB de test).
- Ajouter Actuator/Micrometer sur latence erreurs Jira.

### Critères de validation
- Pipeline CI avec tests auto.
- Visibilité sur performances et erreurs d'intégration Jira.

---

## Proposition de cadence de travail (ensemble)

- **Sprint A (rapide)**: Étape 1 + 2.
- **Sprint B**: Étape 3.
- **Sprint C**: Étape 4 + 5.

---

## Première étape conseillée pour démarrer ensemble

Commencer par **Étape 1 (sécurité/config)**, car c'est le gain le plus immédiat (risque de fuite de secrets) et cela débloque des pratiques de déploiement propres pour la suite.
