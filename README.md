# INSAF Backend

Java 21, Spring Boot 3.5.7, Maven, MySQL 8 et Flyway.

## Démarrage

1. Créer une base locale avec `scripts/setup.sql` et définir `DB_URL`, `DB_USERNAME` et `DB_PASSWORD`. Les valeurs de `.env.example` sont illustratives et Spring ne charge pas automatiquement un fichier `.env`.
2. Lancer `mvn spring-boot:run`. Le port par défaut est `8888` et peut être remplacé par `PORT`.
3. Vérifier `GET http://localhost:8888/actuator/health`.

## Identités locales fictives

Le mode local est désactivé par défaut. Il exige à la fois le profil `dev` et `INSAF_DEV_AUTH_ENABLED=true`. Les profils `prod` et `production` interdisent ce mode, même avec `dev` et le drapeau. Le profil `dev` configure une écoute sur `127.0.0.1`. Ne pas publier le serveur ou le proxy Vite avec ce profil.

Sur une base **réservée aux données fictives**, associer `dev-alice` et `dev-bob` aux identifiants de deux militants fictifs distincts via `INSAF_DEV_ALICE_MILITANT_ID` et `INSAF_DEV_BOB_MILITANT_ID`. Définir `INSAF_DEV_ADMIN_PASSWORD`, `INSAF_DEV_ALICE_PASSWORD` et `INSAF_DEV_BOB_PASSWORD` avec trois mots de passe fictifs distincts d’au moins 12 caractères. Exporter ces variables dans le processus Spring, puis lancer le serveur.

```bash
SPRING_PROFILES_ACTIVE=dev INSAF_DEV_AUTH_ENABLED=true mvn spring-boot:run
curl -i http://localhost:8888/api/me
curl -i -u dev-alice http://localhost:8888/api/me
```

Le premier appel à `/api/me` donne `401`. Le second demande le mot de passe à l’invite et retourne par exemple `{"subject":"dev:alice","username":"dev-alice","roles":["MILITANT"]}`. Les identifiants locaux d’association restent côté serveur pour la résolution du profil.

## Contrats et autorisations

| Route | Accès |
|---|---|
| `GET /actuator/health` | Public |
| `GET /api/me`, `GET /api/csrf` | Utilisateur authentifié |
| `GET /api/echeances` | Toutes les échéances pour un administrateur. Échéances de son identité serveur pour un militant associé |
| `POST /api/echeances`, `GET/POST /api/militants`, `GET/POST /api/categories`, `GET/POST /api/dons`, `GET /actuator/info` | Administrateur |

Pour un `POST`, récupérer d’abord le jeton avec `GET /api/csrf`, puis renvoyer sa valeur dans l’en-tête `X-CSRF-TOKEN` avec le cookie de la même session et l’authentification appropriée. Lors de la création d’une échéance, un administrateur sélectionne un militant cible et le serveur vérifie son existence. Pour la lecture par un militant, le serveur ignore les identifiants fournis par la requête.

## Intégration à la plateforme d’adhésion

`CurrentUserService` lit un `AuthenticatedUser` depuis le contexte Spring Security. L’implémentation future de `MembershipSecurityConfigurer` devra configurer la validation OIDC ou la validation de la session d’adhésion, installer le profil vérifié comme principal et dériver les autorités Spring `ROLE_ADMIN` ou `ROLE_MILITANT` des rôles de ce profil. La résolution du militant local se fera côté serveur à partir du sujet vérifié et de l’association approuvée. Pour OIDC, utiliser un sujet unique par émetteur. Le mode local et l’intégration sont mutuellement exclusifs.

Hors mode local, la chaîne refuse les API tant qu’aucune intégration n’est configurée. Le frontend utilise une URL relative `/api`. Prévoir un reverse proxy de même origine pour le déploiement et conserver la protection CSRF pour une authentification par cookie. En développement, CORS autorise par défaut `http://localhost:5173`. Le proxy Vite permet de conserver des requêtes de même origine côté navigateur.

Flyway et les tables métier existantes servent toujours de référence. Les données de développement doivent rester fictives. Les paiements, la validation des dons et la synchronisation CRM restent à implémenter.

```bash
mvn clean verify
```

## Cotisations

`GET /api/echeances` renvoie la catégorie (code et libellé), la période, la date limite, les montants dû et payé, le solde et le statut enregistré. Spring calcule le solde avec `BigDecimal`. Un militant voit uniquement ses échéances, selon l’association serveur de son profil. Le catalogue complet `GET /api/categories` est réservé aux administrateurs.

Un administrateur peut créer une catégorie avec `POST /api/categories` et une échéance avec `POST /api/echeances`. Les deux appels nécessitent la protection CSRF décrite plus haut. Une nouvelle échéance reprend le montant de la catégorie et démarre à `EN_ATTENTE` avec un montant payé de zéro. Un code de catégorie déjà utilisé ou une échéance déjà présente pour le même militant, la même catégorie et les mêmes dates de période entraîne un `409 Conflict`. Les contraintes uniques de Flyway V1 arbitrent également les créations concurrentes. Aucune nouvelle migration SQL n’est nécessaire.

## Paiements simulés

La migration Flyway `V2__transaction_paiement.sql` crée la table `transaction_paiement` et ajoute un index unique sur l’échéance pour garantir son rattachement au bon militant. Elle conserve les lignes financières existantes, y compris celles de la table `paiement`. Flyway exécute les migrations au démarrage. Attendre l’autorisation avant de démarrer cette version contre une base contenant des données réelles.

Démarrer une instance MySQL locale et créer une base fictive nommée `insaf_payments_test`. Configurer les variables d’authentification fictive décrites plus haut, puis démarrer Spring :

```bash
DB_URL=jdbc:mysql://127.0.0.1:3306/insaf_payments_test \
SPRING_PROFILES_ACTIVE=dev INSAF_DEV_AUTH_ENABLED=true \
APP_PAIEMENTS_SIMULATION=true mvn spring-boot:run
```

Adapter le port MySQL si l’instance isolée écoute sur un autre port. Définir explicitement `DB_USERNAME` et `DB_PASSWORD` pour cette instance. Lorsque le simulateur est demandé sous `dev` ou `test`, Spring vérifie la cible MySQL locale avant Flyway. Le drapeau est désactivé par défaut. `prod` et `production` excluent les simulateurs et leur route, même avec `dev` ou `test`. Les clients officiels restent à intégrer.

| Méthode et route | Usage |
|---|---|
| `GET /api/paiements/operateurs` | Liste Bankily, Sedad et Masrvi avec leur disponibilité et l’indicateur de simulation |
| `POST /api/paiements` | Crée une réservation et renvoie `202`. Corps INSAF avec `echeanceId`, `operateur` et `montant` sous forme de chaîne décimale. Jeton CSRF et en-tête `Idempotency-Key` UUID requis |
| `GET /api/paiements/{reference}` | Détail d’un paiement du militant courant |
| `GET /api/paiements` | Historique personnel. Paramètres facultatifs `page`, `taille`, `echeanceId` |
| `POST /api/paiements/{reference}/simulation?scenario=SUCCES` | Déclenche le résultat fictif construit côté serveur. Scénarios `SUCCES`, `ECHEC`, `ANNULATION`. Route de simulation uniquement, avec CSRF |

Les routes exigent un militant authentifié associé localement. Une tentative dépassant le solde après réservations renvoie `409`. Une relance avec la même clé et les mêmes paramètres retrouve la transaction. Une confirmation vérifiée crédite l’échéance et passe le paiement à `CONFIRME` dans la même transaction. Le statut de l’échéance reste celui enregistré. Un résultat contradictoire tardif positionne un indicateur de rapprochement que le détail expose.

Pour lancer également les tests d’intégration MySQL, fournir une base fictive dédiée et renseigner `INSAF_PAYMENTS_IT_URL`, `INSAF_PAYMENTS_IT_USER` et `INSAF_PAYMENTS_IT_PASSWORD` avant `mvn verify`. Le test de rollback ajoute puis retire une contrainte temporaire sur `transaction_paiement` dans cette base.
