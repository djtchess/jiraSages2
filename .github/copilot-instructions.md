# Copilot Instructions for JiraSages2

## Overview
This codebase is a full-stack Agile/Scrum management tool integrating Angular (front-end) and Spring Boot (back-end). It connects to Jira via REST APIs and manages sprints, boards, burnup charts, and calendars.

## Architecture
- **Front-end**: Angular 18, located in `front/`. Key modules: `app/`, `service/`, `model/`, `burnup-chart/`, `calendar/`, `sprint-list/`, etc.
- **Back-end**: Spring Boot 3, located in `back/`. Main packages: `controller/`, `service/`, `entities/`, `dto/`, `repository/`, `model/`, `utils/`.
- **Integration**: The front-end communicates with the back-end via REST endpoints (see `JiraService` in `front/src/service/jira.service.ts`). The back-end connects to Jira using `JiraApiClient`.

## Developer Workflows
### Front-end
- **Dev server**: `cd front; ng serve` (http://localhost:4200)
- **Build**: `ng build` (outputs to `dist/`)
- **Unit tests**: `ng test` (Karma)
- **E2E tests**: `ng e2e` (requires additional setup)
- **Scaffolding**: Use Angular CLI (`ng generate component ...`)

### Back-end
- **Dev server**: `cd back; ./mvnw spring-boot:run` (http://localhost:8088)
- **Build**: `./mvnw clean package`
- **Tests**: `./mvnw test`
- **Config**: See `application.properties` for DB/Jira credentials and port settings.

## Key Patterns & Conventions
- **REST API**: All main endpoints are under `/api/jira` (see `JiraController`).
- **DTOs**: Data transfer objects are in `dto/` and used for API responses.
- **Entities**: Domain models in `entities/`.
- **Services**: Business logic in `service/` (e.g., `SprintService`, `SprintStatsService`).
- **Angular Routing**: Defined in `app.routes.ts`.
- **Material UI**: Angular Material is used for UI components.
- **Cross-Origin**: Back-end allows CORS for Angular dev (`@CrossOrigin(origins = "*")`).

## Integration Points
- **Jira**: Credentials and base URL in `application.properties`. API client: `JiraApiClient.java`.
- **Database**: PostgreSQL, configured in `application.properties`.

## Examples
- To fetch sprints for a project: `GET /api/jira/projects/{projectKey}/sprints` (see `JiraService.getSprintsForProject`)
- To get burnup data: `GET /api/jira/burnup/{sprintId}`

## Tips for AI Agents
- Always update both front and back for new features that cross boundaries.
- Respect DTO/entity separation for API design.
- Use Angular CLI and Spring Boot Maven wrapper for all builds/tests.
- Reference `README.md` and `HELP.md` for more details.

---
_Last updated: 2025-11-24_
