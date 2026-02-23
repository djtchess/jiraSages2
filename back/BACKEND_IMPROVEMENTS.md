# Analyse du back Java (Spring Boot) et pistes d’amélioration

## Synthèse rapide

Le backend est fonctionnel et déjà structuré en couches (`controller`, `service`, `repository`, `dto`, `entities`), mais plusieurs points peuvent améliorer significativement la qualité:

1. **architecture et séparation des responsabilités** (certaines classes trop volumineuses),
2. **sécurité et configuration** (secrets en clair, CORS permissif),
3. **robustesse API** (gestion d’erreurs, validation, contrats REST),
4. **maintenabilité/outillage** (tests, qualité statique, dépendances).

---

## 1) Architecture & conception

### 1.1 Réduire la taille et la responsabilité de `JiraApiClient`
`JiraApiClient` concentre beaucoup de logique (HTTP, parsing JSON, pagination, cache changelog, calculs métiers) dans une seule classe, ce qui augmente le couplage et la difficulté de test.  

**Recommandation** (progressive):
- extraire un `JiraHttpClient` (requêtes/réponses + retry/timeout),
- extraire un `JiraParser` (mapping JsonNode -> DTO/domain),
- extraire un `ChangelogCacheService` (TTL + invalidation),
- garder `JiraApiClient` comme façade d’orchestration.

**Bénéfice**: tests unitaires plus ciblés, code plus lisible et évolutif.

### 1.2 Clarifier le contrat des couches
On trouve des conversions DTO/Entity faites dans les contrôleurs (`SprintMapper` appelé directement dans `SprintController`).

**Recommandation**:
- déplacer les mappings dans les services applicatifs,
- garder les contrôleurs “minces” (entrée/sortie HTTP + validation),
- centraliser les règles métier dans la couche service.

**Bénéfice**: séparation claire des responsabilités et réduction des effets de bord.

---

## 2) Sécurité & configuration

### 2.1 Retirer les secrets du code/config versionnée
Le fichier `application.properties` contient des identifiants DB et un token Jira en clair.

**Recommandation immédiate**:
- révoquer/renouveler le token exposé,
- migrer les secrets vers variables d’environnement (`SPRING_DATASOURCE_PASSWORD`, `JIRA_API_TOKEN`, etc.),
- conserver dans le repo un `application.properties` sans secrets + un `application-example.properties` documenté.

### 2.2 Durcir CORS
Plusieurs contrôleurs utilisent `@CrossOrigin(origins = "*")`.

**Recommandation**:
- définir une config CORS globale via `WebMvcConfigurer` ou Spring Security,
- autoriser explicitement les domaines front (`dev`, `staging`, `prod`),
- restreindre méthodes et headers au strict nécessaire.

### 2.3 Externaliser les paramètres réseau/proxy
Le proxy HTTP est codé en dur dans `JiraApiClient`.

**Recommandation**:
- passer par propriétés (`jira.proxy.host`, `jira.proxy.port`),
- rendre la config optionnelle selon environnement.

**Bénéfice global**: meilleure sécurité opérationnelle et déploiement multi-env simplifié.

---

## 3) Robustesse API & qualité de service

### 3.1 Uniformiser la gestion d’erreurs
Plusieurs méthodes exposent `throws Exception` dans les contrôleurs.

**Recommandation**:
- créer des exceptions métier dédiées (`JiraIntegrationException`, `SprintNotFoundException`, ...),
- enrichir `GlobalExceptionHandler` avec un format d’erreur stable (code, message, timestamp, path),
- supprimer les `throws Exception` des signatures REST publiques.

### 3.2 Ajouter de la validation d’entrée
Les payloads et paramètres ne semblent pas validés systématiquement.

**Recommandation**:
- activer Bean Validation (`@Valid`, `@NotNull`, `@Positive`, etc.),
- valider les bornes (IDs, percent 0..100, dates),
- renvoyer des erreurs 400 explicites et homogènes.

### 3.3 Stabiliser les contrats REST
Le endpoint `/projects/{projectKey}/sprints` utilise actuellement un board id codé en dur (`6`).

**Recommandation**:
- rendre le board réellement paramétrable (`/boards/{boardId}/sprints`),
- ou documenter explicitement la stratégie si le board est unique.

**Bénéfice**: API plus prévisible et plus simple à consommer côté front.

---

## 4) Données, JPA et performance

### 4.1 Éviter les appels externes coûteux dans la couche contrôleur
Certaines routes assemblent des données Jira + DB à la volée.

**Recommandation**:
- mettre en cache court (Caffeine/Spring Cache) les appels Jira fréquents,
- tracer latence et taux d’échec (Micrometer + Actuator),
- isoler les appels externes derrière une interface testable.

### 4.2 Encadrer les transactions et accès DB
Vérifier que les opérations multi-écritures critiques sont encapsulées en `@Transactional`.

### 4.3 Surveiller N+1 et volume de payload
Selon les entités exposées, limiter les chargements inutiles (DTO projetés, pagination, requêtes ciblées).

**Bénéfice global**: meilleures perfs et stabilité sous charge.

---

## 5) Qualité de code & outillage

### 5.1 Moderniser et aligner les dépendances
`pom.xml` contient des dépendances historiques/commentées (ex: `junit-jupiter-engine` figé, libs Apache HttpClient explicites) qui méritent nettoyage.

**Recommandation**:
- s’appuyer au maximum sur le BOM Spring Boot,
- supprimer les dépendances redondantes/commentées,
- ajouter `maven-enforcer-plugin` (Java version, convergence dépendances).

### 5.2 Renforcer les tests
Le dossier de test semble minimal (test de contexte uniquement).

**Recommandation**:
- tests unitaires des services métier,
- tests `@WebMvcTest` pour les contrôleurs,
- tests d’intégration ciblés (repository + base isolée Testcontainers),
- contrat d’erreur HTTP testé.

### 5.3 Ajouter des garde-fous de qualité
- intégrer Spotless/Checkstyle/PMD (au moins un outil),
- intégrer JaCoCo (seuil minimal de couverture),
- pipeline CI: `mvn -q test` + qualité statique + packaging.

---

## Plan d’action recommandé (priorisé)

1. **Semaine 1 – Sécurité/config**: retrait secrets versionnés, CORS restreint, proxy externalisé.
2. **Semaine 2 – Robustesse API**: exceptions métier + validation Bean Validation + format d’erreur uniforme.
3. **Semaine 3 – Architecture**: découpage progressif de `JiraApiClient` en composants spécialisés.
4. **Semaine 4 – Qualité**: lot de tests backend + outillage statique + pipeline CI standardisé.

Ce plan donne des gains immédiats sur le risque opérationnel (sécurité), puis améliore progressivement maintenabilité et vitesse de livraison.
