# INSAF Backend — MVP sans CRM

Java 21, Spring Boot 3.5.7, Maven, MySQL 8, Flyway.

## Préparation

1. Installer Java 21, Maven et MySQL 8.
2. Créer la base et le compte MySQL avec `scripts/setup.sql` (adapter le mot de passe et les permissions).
3. Configurer les variables d'environnement `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `ADMIN_USERNAME`, `ADMIN_PASSWORD` (minimum 12 caractères), `CORS_ORIGIN`.
4. Lancer `mvn spring-boot:run`.
5. Vérifier `GET http://localhost:8080/actuator/health`.

## Authentification provisoire

Les endpoints `/api/**` sont protégés par HTTP Basic (compte admin de développement). Ne pas déployer publiquement avec cette authentification : implémenter OIDC/JWT, MFA, RBAC et audit avant production. Utiliser HTTPS même en environnement de test partagé.

## API MVP

- `GET/POST /api/militants` (militants locaux provisoires)
- `GET/POST /api/categories`
- `GET/POST /api/echeances`
- `GET/POST /api/dons`

Exemple :

```bash
curl -u "$ADMIN_USERNAME:$ADMIN_PASSWORD" -H 'Content-Type: application/json' -d '{"nom":"Test","prenom":"Militant","telephone":"00000000"}' http://localhost:8080/api/militants
curl -u "$ADMIN_USERNAME:$ADMIN_PASSWORD" -H 'Content-Type: application/json' -d '{"code":"STANDARD","libelle":"Cotisation standard","montant":100,"periodicite":"MENSUELLE"}' http://localhost:8080/api/categories
```

## Limites explicites

- Le CRM n'est pas connecté : `militant_reference` contient des militants locaux temporaires, avec `crm_militant_id` facultatif.
- Ne pas saisir de données réelles sensibles dans cet environnement de démonstration.
- Les paiements, reçus, validation des dons, rapprochement, reporting et synchronisation CRM ne sont **pas implémentés**.
- Aucun paiement n'est confirmé automatiquement et aucun don n'est comptabilisé comme validé.
- `ddl-auto: validate` et Flyway assurent les migrations ; ne pas utiliser `ddl-auto: update` en production.
- Prévoir un adaptateur CRM lecture seule et une procédure de rapprochement des identifiants avant import réel.
