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

### 1.3 Découpage en tâches (pas à pas) pour implémenter 1.1 et 1.2

Ci-dessous un plan opérationnel en petites tâches livrables, pensé pour être exécuté sur plusieurs PR courtes.

#### Epic A — Refactor `JiraApiClient` en façade orchestratrice

**A1 — Cartographier les responsabilités actuelles (1 PR de préparation)**
- Lister les méthodes de `JiraApiClient` par catégorie: HTTP, parsing, pagination, cache, logique métier.
- Identifier les dépendances externes (ObjectMapper, RestTemplate/HttpClient, proxy, propriétés).
- Produire un mini schéma de flux (appel contrôleur/service -> JiraApiClient -> Jira).

**Critères d’acceptation**
- Une documentation courte existe dans le repo (ou ADR) avec le périmètre exact à extraire.
- Les zones à risque sont identifiées (méthodes les plus appelées, comportements de cache).

---

**A2 — Extraire `JiraHttpClient` (sans changer le comportement fonctionnel)**
- Créer une classe dédiée aux appels HTTP Jira (GET/POST + gestion headers/auth).
- Y déplacer: construction URL, timeout, retry, proxy, gestion status code.
- Conserver `JiraApiClient` comme appelant de `JiraHttpClient`.

**Critères d’acceptation**
- `JiraApiClient` ne construit plus directement les requêtes HTTP.
- Les tests existants passent et la signature publique de `JiraApiClient` reste stable.

---

**A3 — Extraire `JiraParser` (mapping JsonNode -> DTO/domain)**
- Créer une classe dédiée à la désérialisation et aux conversions JSON.
- Y déplacer les méthodes de parsing (issues, sprints, changelog, etc.).
- Isoler les cas d’erreur de parsing dans des exceptions explicites.

**Critères d’acceptation**
- `JiraApiClient` ne manipule plus directement la structure JSON brute hors orchestration.
- Des tests unitaires ciblent le parsing sur des payloads réels/fixtures.

---

**A4 — Extraire `ChangelogCacheService` (TTL + invalidation)**
- Créer un service de cache encapsulant la stratégie de TTL.
- Y déplacer les structures de cache et règles d’invalidation.
- Exposer une API simple (`getOrLoad`, `evict`, `clearExpired`).

**Critères d’acceptation**
- Aucune structure de cache n’est maintenue directement dans `JiraApiClient`.
- Comportement cache inchangé validé par tests unitaires.

---

**A5 — Simplifier `JiraApiClient` en façade d’orchestration**
- Réduire `JiraApiClient` à la coordination entre `JiraHttpClient`, `JiraParser`, `ChangelogCacheService`.
- Supprimer les méthodes privées devenues obsolètes.
- Mettre à jour JavaDoc et diagramme de dépendances.

**Critères d’acceptation**
- `JiraApiClient` a une taille et une complexité réduites (méthodes plus courtes, responsabilités limitées).
- Les tests d’intégration de l’appel Jira restent au vert.

---

#### Epic B — Clarifier le contrat Controller ↔ Service ↔ Mapper

**B1 — Définir la règle d’architecture cible**
- Règle: le contrôleur gère HTTP + validation uniquement.
- Règle: les mappings DTO/Entity sont appelés dans les services applicatifs.
- Documenter ces règles dans une section “Conventions d’architecture”.

**Critères d’acceptation**
- Une convention écrite est partagée et validée par l’équipe.
- Les nouveaux développements doivent suivre cette règle.

---

**B2 — Migrer `SprintController` vers un contrôleur mince**
- Déplacer les appels directs à `SprintMapper` du contrôleur vers `SprintService`.
- Adapter la signature du service pour retourner des DTO prêts pour la réponse HTTP.
- Conserver les validations d’entrée côté contrôleur (`@Valid`, vérifications de params).

**Critères d’acceptation**
- `SprintController` ne dépend plus de `SprintMapper`.
- Le comportement des endpoints reste identique (contrat JSON inchangé).

---

**B3 — Répliquer le modèle sur les autres contrôleurs**
- Scanner les contrôleurs restants et identifier les appels mapper/transformations métier.
- Déplacer ces transformations dans les services associés.
- Nettoyer les imports et dépendances inutiles dans la couche web.

**Critères d’acceptation**
- Les contrôleurs ne contiennent plus de logique métier ni mapping complexe.
- Les services centralisent les règles de transformation.

---

**B4 — Ajouter des garde-fous de non-régression architecturelle**
- Ajouter des tests d’architecture (ex: ArchUnit) pour interdire `controller -> mapper` direct si souhaité.
- Ajouter une checklist PR: “contrôleur mince”, “mapping en service”, “pas de logique métier en web”.

**Critères d’acceptation**
- Une PR violant la règle remonte une alerte (test ou revue structurée).

---

#### Ordonnancement conseillé (2 à 3 semaines)

1. **Semaine 1**: A1, A2, B1
2. **Semaine 2**: A3, B2
3. **Semaine 3**: A4, A5, B3, B4

#### Définition de “Done” (globale)
- Tous les tests existants + nouveaux tests unitaires passent.
- Aucun endpoint fonctionnellement cassé.
- La dette de responsabilité de `JiraApiClient` est réduite et mesurable.
- Les contrôleurs sont minces et les mappings sont déplacés en service.

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
