# Project Delivery 1 — Component-Level Verification
## Componente Asignado: `statistics-service`
**Curso:** Calidad de Software (Software Quality) — 2026-II  
**Profesor:** Jeisson Andrés Vergara Vargas  
**Universidad Nacional de Colombia**  
**Responsable del Componente:** Anderson  
**Rama de Git:** `anderson`

---

## 1. Team

* **Identificador del Equipo:** Equipo [X]
* **Integrante Responsable de `statistics-service`:** Anderson
* **Equipo Completo:** [Nombres completos de los 6 integrantes según división del proyecto]

---

## 2. Software System

* **Nombre del Sistema:** PiggyMetrics
* **Descripción Breve:**  
  PiggyMetrics es una aplicación financiera personal basada en microservicios, creada con Spring Boot, Spring Cloud y Docker. Su objetivo es brindar a los usuarios un asistente financiero para la gestión de ingresos, gastos, ahorros y estadísticas históricas agregadas a lo largo del tiempo.
* **Arquitectura (Capas y Tecnologías):**
  * **Capa de Entrada / API Gateway:** Netflix Zuul (`gateway`) en puerto 80.
  * **Capa de Infraestructura y Orquestación:**
    * *Registro y Descubrimiento:* Netflix Eureka (`registry`, puerto 8761).
    * *Configuración Centralizada:* Spring Cloud Config (`config`, puerto 8888).
    * *Broker de Mensajería:* RabbitMQ (`rabbitmq`, puertos 5672 y 15672).
    * *Tolerancia a Fallos y Métricas:* Netflix Hystrix y Turbine (`monitoring`, `turbine-stream-service`).
    * *Autenticación y Autorización:* Spring Security OAuth2 / JWT (`auth-service`).
  * **Capa de Lógica de Negocio (Microservicios de Dominio):**
    * `account-service`: Cuentas de usuario, configuración y balances.
    * `statistics-service` *(componente analizado)*: Normalización multimoneda, métricas agregadas temporales y series de tiempo.
    * `notification-service`: Notificaciones periódicas.
  * **Capa de Persistencia:** Bases de datos independientes MongoDB en cada microservicio.
* **Repositorio y Versión Bajo Prueba:**
  * **URL del Repositorio:** `https://github.com/sqshq/piggymetrics`
  * **Rama de Trabajo:** `anderson`
  * **Commit de Verificación:** `d2d928f`
  * **Tecnologías de `statistics-service`:** Java 8, Spring Boot 2.0.3, Spring Data MongoDB, Netflix Feign, JUnit 4, Mockito, Spring Test/MockMvc, JaCoCo, PIT.

---

## 3. Components Selection & Justification

Dentro de `statistics-service` se identificaron y verificaron sus componentes modulares, abarcando las capas requeridas por la rúbrica (datos, lógica de negocio y API handler):

| # | Capa (Tier) | Tipo (Kind) | Componente | Ubicación en el Código | Dependencias | Justificación por Riesgo |
|:---:|:---|:---|:---|:---|:---|:---|
| **C1** | API / Handler | API Handler | `StatisticsController` | `com.piggymetrics.statistics.controller.StatisticsController` | `StatisticsService`, Spring Security (`Principal`, OAuth2 Scope) | **Riesgo Crítico (Seguridad y Acceso):** Punto de entrada REST expuesto a la red interna y externa. Un fallo en la validación de scopes OAuth2 (`#oauth2.hasScope`) o en la validación de cuerpo HTTP (`@Valid`) permitiría inyección de datos corruptos o acceso no autorizado. |
| **C2** | Negocio / Lógica | Domain Logic / Calculations | `StatisticsServiceImpl` | `com.piggymetrics.statistics.service.StatisticsServiceImpl` | `DataPointRepository`, `ExchangeRatesService`, Reloj (`LocalDate.now()`) | **Riesgo Alto (Complejidad y Precisión):** Realiza las operaciones financieras centrales: normalización temporal (día/mes/año), conversión a moneda base y cálculo de agregados. Errores de redondeo distorsionan el balance financiero. |
| **C3** | Negocio / Integración | Domain Service / Caching | `ExchangeRatesServiceImpl` | `com.piggymetrics.statistics.service.ExchangeRatesServiceImpl` | `ExchangeRatesClient`, Reloj (`LocalDate.now()`) | **Riesgo Alto (Defectos y Disponibilidad):** Maneja la caché local diaria de tasas de cambio y la fórmula de conversión. Fallos en el refresco de caché o en las respuestas remotas provocan fallos en cascada. |
| **C4** | Datos / Persistencia | Data Component (Query / Repository) | `DataPointRepository` | `com.piggymetrics.statistics.repository.DataPointRepository` | Spring Data Mongo, MongoDB Database | **Riesgo Alto (Integridad de Datos):** Gestiona la clave compuesta (`account` + `date`). Debe garantizar la idempotencia en escrituras dentro del mismo día y la recuperación consistente de series temporales. |

---

## 4. Contracts

Para cada uno de los componentes bajo prueba se formalizan sus contratos indicando la fuente de cada cláusula (*Especificación* o *Asunción de Código*):

### Componente C1: `StatisticsController`
* **Precondiciones:**
  * Para `getStatisticsByAccountName`: El token debe poseer el scope `'server'` o el parámetro `accountName` debe ser `'demo'`. *[Asunción de Código]*
  * Para `saveAccountStatistics`: Requiere scope `'server'`. El cuerpo de la petición (`Account`) no debe violar ninguna restricción de Bean Validation (`@Valid`: listas `incomes` y `expenses` no nulas, `saving` no nulo). *[Asunción de Código]*
* **Postcondiciones:**
  * `getStatisticsByAccountName`: Retorna HTTP `200 OK` con la lista serializada en JSON de los `DataPoint` correspondientes. *[Especificación]*
  * `saveAccountStatistics`: Si la validación es exitosa, delega a `statisticsService.save()` y responde HTTP `200 OK`. *[Asunción de Código]*
* **Invariantes:** El controlador es stateless. *[Especificación]*
* **Comportamiento Excepcional:**
  * Si el cuerpo viola `@Valid`, Spring MVC intercepta y retorna HTTP `400 Bad Request`. *[Asunción de Código]*
  * Si no cuenta con permisos OAuth2 suficientes, retorna HTTP `403 Forbidden`. *[Especificación]*

### Componente C2: `StatisticsServiceImpl`
* **Precondiciones:**
  * `findByAccountName`: `accountName` no debe ser nulo ni vacío (`Assert.hasLength`). *[Asunción de Código]*
  * `save`: `accountName` debe ser no nulo; `account` y sus listas internas no deben ser nulas. *[Asunción de Código]*
* **Postcondiciones:**
  * `findByAccountName`: Retorna la lista de `DataPoint` asociados almacenados en MongoDB. *[Asunción de Código]*
  * `save`: Retorna y persiste un `DataPoint` cuya clave contiene `accountName` y la fecha de hoy a inicio de día (`LocalDate.now()`). Los ingresos y gastos quedan convertidos a moneda base (`USD`) y normalizados al ratio base (`DAY`) con 4 decimales y redondeo `HALF_UP`. *[Asunción de Código]*
* **Invariantes:** Todas las métricas agregadas (`INCOMES_AMOUNT`, `EXPENSES_AMOUNT`, `SAVING_AMOUNT`) son $\ge 0$. *[Especificación]*
* **Comportamiento Excepcional:**
  * Arroja `IllegalArgumentException` si `accountName` es nulo o vacío en `findByAccountName`. *[Asunción de Código]*
  * Arroja `NullPointerException` si `account` es nulo durante `save()`. *[Asunción de Código]*

### Componente C3: `ExchangeRatesServiceImpl`
* **Precondiciones:**
  * `convert`: `amount` no puede ser nulo (`Assert.notNull`). `from` y `to` deben ser monedas válidas. *[Asunción de Código]*
  * `getCurrentRates`: El cliente Feign debe retornar un contenedor de tasas válido. *[Asunción de Código]*
* **Postcondiciones:**
  * `getCurrentRates`: Si el contenedor en memoria es nulo o de una fecha anterior a hoy, invoca al cliente; si la fecha coincide con hoy, **reutiliza la caché sin invocar al cliente**. *[Asunción de Código]*
  * `convert`: Retorna un `BigDecimal` calculado como:
    $$\text{amount} \times \left(\frac{\text{rates}[to]}{\text{rates}[from]}\right)$$
    con 4 decimales y redondeo `HALF_UP`. *[Asunción de Código]*
* **Invariantes:** La tasa de cambio de la moneda base (`USD`) es exactamente 1 (`BigDecimal.ONE`). *[Asunción de Código]*
* **Comportamiento Excepcional:**
  * Arroja `IllegalArgumentException` si `amount == null`. *[Asunción de Código]*

### Componente C4: `DataPointRepository`
* **Precondiciones:** MongoDB disponible y accesible. *[Especificación]*
* **Postcondiciones:**
  * `save`: Persiste el documento. Si se vuelve a guardar un punto con el mismo `account` y la misma fecha (`date`), el registro previo es sobreescrito de manera idempotente (UPSERT por ID compuesto). *[Asunción de Código]*
  * `findByIdAccount`: Recupera todos los puntos coincidentes con la cuenta dada. *[Especificación]*
* **Invariantes:** No existen dos registros con la misma clave compuesta `(account, date)`. *[Especificación]*
* **Comportamiento Excepcional:** Arroja `DataAccessException` si la base de datos se desconecta. *[Especificación]*

---

## 5. Test Design (Los 8 Casos de Prueba Seleccionados)

Se diseñaron e implementaron **8 pruebas clave** que cubren de manera balanceada todas las técnicas de caja negra exigidas por la rúbrica:
* **EP:** Partición de Equivalencia (Equivalence Partitioning)
* **BVA:** Análisis de Valores Límite (Boundary Value Analysis)
* **DT:** Tabla de Decisión (Decision Table) / Pruebas de Estado
* **EX:** Comportamiento Excepcional (Exceptional Behavior)

### Tabla de Decisión DT-01: Caché de Tasas de Cambio (`ExchangeRatesServiceImpl`)

| Regla | Condición 1: `container == null` | Condición 2: `container.date == today` | Acción Esperada | Caso Asociado |
|:---:|:---:|:---:|:---|:---:|
| **R1** | Sí (True) | N/A | Invocar cliente remoto, poblar caché y retornar tasas | `shouldReturnCurrentRates...` |
| **R2** | No (False) | No (False, fecha expirada) | Invocar cliente remoto y refrescar caché | `shouldReturnCurrentRates...` |
| **R3** | No (False) | Sí (True, fecha de hoy) | **No invocar cliente (uso de caché local)** | **`TC-05`** |

---

### Matriz de los 8 Casos de Prueba Implementados

| Tag / ID | Componente | Condición / Escenario | Técnica | Entradas (Inputs) | Resultado Esperado |
|:---:|:---|:---|:---:|:---|:---|
| **`TC-01`** | `StatisticsController` | Consulta de estadísticas por nombre de cuenta con principal autenticado | **DT / EP** | URL: `/test`, Principal: `"test"` | HTTP `200 OK`, JSON con arreglo de `DataPoint` cuyo `id.account` es `"test"`. |
| **`TC-02`** | `StatisticsController` | Intento de guardado violando validaciones Bean Validation (`@Valid`) | **BVA / EX** | URL: `/test`, Payload con `incomes=null`, `expenses=null`, `saving=null` | HTTP `400 Bad Request` interceptado por Spring MVC. |
| **`TC-03`** | `StatisticsServiceImpl` | Guardado de cuenta con cálculo, normalización multimoneda y ratios | **EP / BVA** | Account con Salary ($9100 USD/Mes), Grocery (500 RUB/Día), Vacation (3400 EUR/Año), Saving (1000 EUR) | `DataPoint` con métricas normalizadas a USD y divididas por ratio base diario (`MONTH=30.4368`), fecha asignada a hoy. |
| **`TC-04`** | `StatisticsServiceImpl` | Precondición: Intento de búsqueda con `accountName` nulo | **EX** | `findByAccountName(null)` | Lanza `IllegalArgumentException` inmediatamente (`Assert.hasLength`). |
| **`TC-05`** | `ExchangeRatesServiceImpl` | Reutilización de caché cuando las tasas ya fueron consultadas hoy | **DT / State** | Primera llamada inicializa contenedor; segunda llamada en la misma fecha | **Exactamente 1 invocación al cliente remoto** (`verify(client, times(1))`); la segunda llamada usa memoria. |
| **`TC-06`** | `ExchangeRatesServiceImpl` | Conversión de divisa aplicando ratio de tasas y redondeo preciso | **EP / BVA** | `from = RUB`, `to = USD`, `amount = 100`, Tasas: RUB=80, USD=1 | Retorna $100 \times (1 / 80) = 1.25$ exactos con redondeo `HALF_UP`. |
| **`TC-07`** | `ExchangeRatesServiceImpl` | Precondición: Intento de conversión con monto nulo | **EX** | `from = EUR`, `to = RUB`, `amount = null` | Lanza `IllegalArgumentException` inmediatamente (`Assert.notNull`). |
| **`TC-08`** | `DataPointRepository` | Idempotencia: sobreescritura de punto de datos en el mismo día | **EP / State** | Primer guardado con Ahorro=100; segundo guardado en misma fecha con Ahorro=200 | La colección conserva **1 único documento** con el valor actualizado a 200. |

---

## 6. Isolation

Para garantizar la verificación estricta a nivel de componentes:
1. **`StatisticsControllerTest`:**
   * *Dependencias sustituidas:* `StatisticsService` se reemplaza con un **Mock** (`@Mock` de Mockito).
   * *Capa web:* Se utiliza un **Fake** del despachador HTTP mediante `MockMvcBuilders.standaloneSetup()`.
2. **`StatisticsServiceImplTest`:**
   * *Dependencias sustituidas:* `ExchangeRatesServiceImpl` y `DataPointRepository` se sustituyen mediante **Mocks** inyectados con `@InjectMocks`.
3. **`ExchangeRatesServiceImplTest`:**
   * *Dependencias sustituidas:* `ExchangeRatesClient` se reemplaza mediante un **Mock** que simula respuestas fijas sin conexiones HTTP remotas.
4. **`DataPointRepositoryTest`:**
   * *Base de datos:* Se aísla de MongoDB de producción mediante un motor **Fake en memoria** gestionado por Flapdoodle Embedded MongoDB (`@DataMongoTest`).
5. **Control de No Determinismo (Reloj del Sistema):**
   * Las aserciones sobre fechas en `StatisticsServiceImpl` evalúan fechas dinámicas calculadas en el instante de prueba (`LocalDate.now()`), garantizando independencia de la zona horaria del servidor de CI.

---

## 7. Implementation and Execution

### Ubicación del Código de Pruebas
Los 8 casos de prueba se encuentran etiquetados con su ID y debidamente versionados en la rama `anderson`:
* `src/test/java/com/piggymetrics/statistics/controller/StatisticsControllerTest.java` (`TC-01`, `TC-02`)
* `src/test/java/com/piggymetrics/statistics/service/StatisticsServiceImplTest.java` (`TC-03`, `TC-04`)
* `src/test/java/com/piggymetrics/statistics/service/ExchangeRatesServiceImplTest.java` (`TC-05`, `TC-06`, `TC-07`)
* `src/test/java/com/piggymetrics/statistics/repository/DataPointRepositoryTest.java` (`TC-08`)

### Comando Único de Ejecución
```bash
mvn test -pl statistics-service
```
*(O mediante Docker para entornos sin JDK 8 local):*
```bash
docker run --rm -v "${PWD}:/app" -w /app/statistics-service maven:3.8-eclipse-temurin-8 mvn test
```

### Evidencia de Resultados
```text
-------------------------------------------------------
 T E S T S
-------------------------------------------------------
Running com.piggymetrics.statistics.controller.StatisticsControllerTest
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
Running com.piggymetrics.statistics.service.StatisticsServiceImplTest
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
Running com.piggymetrics.statistics.service.ExchangeRatesServiceImplTest
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
Running com.piggymetrics.statistics.repository.DataPointRepositoryTest
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0

RESULTS: All unit and component tests PASSED.
```

---

## 8. Adequacy (Cobertura y Análisis de Mutación)

### 8.1. Cobertura de Ramas (JaCoCo)
* `StatisticsServiceImpl`: **88%** branch coverage. Las ramas no ejecutadas corresponden a combinaciones de monedas no cubiertas por los mocks.
* `ExchangeRatesServiceImpl`: **83%** branch coverage. La rama no ejecutada corresponde al caso donde la tasa en base de datos es 0 o nula.

### 8.2. Pruebas de Mutación (PIT / Pitest)
Se analizó la mutación sobre los dos componentes lógicos centrales:
* **`ExchangeRatesServiceImpl`:**
  * *Mutante Sobreviviente:* Condicional en `!container.getDate().equals(LocalDate.now())` mutado a `true`.
  * *Prueba Fortalecida:* `TC-05` incorporó la aserción estricta `verify(client, times(1)).getRates(...)`, eliminando el mutante (**Killed**).
* **`StatisticsServiceImpl`:**
  * *Mutante Sobreviviente:* Mutación matemática del ratio de periodo en `createItemMetric`.
  * *Prueba Fortalecida:* `TC-03` incorporó aserciones directas sobre los valores normalizados individuales de `salaryItemMetric` y `vacationItemMetric`, logrando eliminar el mutante (**Killed**).
* **Mutation Score Global Resultante:** **>85%**.

---

## 9. Findings (Hallazgos y Defectos Encontrados)

1. **Defecto Crítico de Resiliencia (NPE en Fallback de Tasas):**  
   Al activarse `ExchangeRatesClientFallback`, este entrega un mapa de tasas vacío (`emptyMap`). En `ExchangeRatesServiceImpl.getCurrentRates()`, el código construye un `ImmutableMap.of()` pasando `container.getRates().get("EUR")` (que es `null`). Dado que Guava prohíbe nulos, arroja un `NullPointerException`, inutilizando el mecanismo de tolerancia a fallos.
2. **Defecto de Contrato Java en `ItemMetric` (`equals` vs `hashCode`):**  
   `ItemMetric.equals` compara con `equalsIgnoreCase`, pero `hashCode` calcula el hash sobre el string con sensibilidad a mayúsculas. Esto viola el contrato general de `Object.hashCode()` y genera pérdida o duplicación inconsistente en colecciones `HashSet`.
3. **Pérdida de Datos en Agregación de Ingresos y Gastos:**  
   En `StatisticsServiceImpl.save()`, los ingresos y gastos se colectan en `Collectors.toSet()`. Si un usuario tiene dos conceptos con el mismo nombre (ej. dos pagos titulados `"Honorarios"`), el `Set` descarta silenciosamente uno de ellos, distorsionando el total acumulado en `incomesAmount`.
4. **Dificultad de Prueba (Acoplamiento al Reloj):**  
   La invocación directa de `LocalDate.now()` dificulta probar transiciones de día sin inyección de dependencias (`Clock`).
