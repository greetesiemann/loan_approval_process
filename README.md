# Loan Approval Process

Coop panga tarkvaraarendaja praktika kodutöö — laenutaotluste esitamise ja kinnitamise protsessi taustarakendus (backend).

## Tehnoloogiad

- **Java 25** + **Spring Boot 4.x**
- **PostgreSQL 15**
- **Flyway**
- **SpringDoc OpenAPI 3**
- **Docker** + **Docker Compose**
- **Lombok**
- **JUnit 5** + **Mockito**

## Käivitamine

### Eeldused
- Docker ja Docker Compose on paigaldatud

### Käivita ühe käsuga

```bash
docker-compose up --build
```

Rakendus käivitub aadressil: `http://localhost:8080`

> Andmebaas käivitub enne rakendust tänu `healthcheck` konfiguratsioonile.

### Lokaalne käivitamine (ilma Dockerita)

1. Käivita andmebaas:
```bash
docker-compose up db
```

2. Käivita rakendus IntelliJ-st või:
```bash
./mvnw spring-boot:run
```

> Lokaalselt ühendub rakendus andmebaasiga `localhost:5433`.

## API dokumentatsioon

Swagger UI on kättesaadav pärast käivitamist:
http://localhost:8080/swagger-ui.html

## API endpointid

| Meetod | URL | Kirjeldus |
|--------|-----|-----------|
| `POST` | `/api/loan/apply` | Esita laenutaotlus |
| `GET` | `/api/loan/{id}` | Vaata taotlust koos maksegraafikuga |
| `POST` | `/api/loan/{id}/approve` | Kinnita taotlus |
| `POST` | `/api/loan/{id}/reject` | Lükka taotlus tagasi |
| `PUT` | `/api/loan/{id}/update` | Uuenda parameetreid ja regenereeri maksegraafik |

### Näide — laenutaotluse esitamine

```json
POST /api/loan/apply
{
  "firstName": "Mari",
  "lastName": "Tamm",
  "personalCode": "60510200222",
  "loanAmount": 15000,
  "loanPeriodMonths": 60,
  "interestMargin": 2.0,
  "baseInterestRate": 2.415
}
```

## Protsessi staatus
STARTED → (age check) → IN_REVIEW → APPROVED/REJECTED

## Seadistatavad parameetrid

Parameetrid on hallatavad andmebaasi `settings` tabeli kaudu:

| Võti | Vaikeväärtus | Kirjeldus |
|------|-------------|-----------|
| `MAX_AGE` | 70 | Maksimaalne kliendi vanus aastates |
| `MIN_AGE` | 18 | Minimaalne kliendi vanus aastates |
| `EURIBOR_6M` | 2.415 | 6 kuu Euribori määr protsentides |

## Projekti struktuur
'''text
src/
├── main/
│   ├── java/ee/cooppank/loanapprovalprocess/
│   │   ├── controller/      # REST API endpointid
│   │   ├── entity/          # JPA entiteedid (LoanApplication, PaymentSchedule, Settings)
│   │   ├── exception/       # Veahaldus (@RestControllerAdvice)
│   │   ├── repository/      # Spring Data JPA repositooriumid
│   │   └── service/         # Äriloogika (LoanService)
│   └── resources/
│       ├── db/migration/    # Flyway SQL migratsioonid (V1, V2, V3)
│       └── application.properties
└── test/                    # Mockito ühikutestid

## Implementeeritud lisaülesanded

- Veahaldus `@RestControllerAdvice` abil
- Mockito ühikutestid
- Dünaamilised parameetrid andmebaasist (Euribor, MAX_AGE, MIN_AGE)
- Maksegraafiku regenereerimine IN_REVIEW staatuses
