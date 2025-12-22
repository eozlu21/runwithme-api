# RunWithMe API

Kotlin + Spring Boot backend for RunWithMe.

## Tech
- Kotlin, Spring Boot 3.x (Web, Security, Validation, Actuator)
- Spring Data JPA + PostgreSQL + PostGIS
- Spring Data MongoDB for DMs
- STOMP over WebSocket for realtime feed/chat
- springdoc-openapi for OpenAPI 3
- JDK 21, Gradle 8

## Quick Start

### Local Development (offline-ready)
```bash
# 1. Copy the environment template
cp .env.example .env

# 2. Start the local dependency stack (Postgres, MinIO, Mailhog)
docker-compose up -d

# 3. Run the application against the local services
./gradlew bootRun

# 4. (Optional) Run build & tests
./gradlew clean build test

# 5. Stop containers when you're done
docker-compose down
```

The `docker-compose.yml` spins up everything the API needs without touching live infrastructure:

| Service | Purpose | Access |
| ------- | ------- | ------ |
| `postgres` | PostGIS-enabled PostgreSQL instance seeded with `runwithme` DB, `appuser/localpass` credentials | `jdbc:postgresql://localhost:5432/runwithme` |
| `minio` + `minio-setup` | S3-compatible storage backing `S3StorageService`, bucket `runwithme-local` auto-created with public read | Console: http://localhost:9001 (user/pass: `minioadmin/minioadmin`), Endpoint: http://localhost:9000 |
| `mailhog` | SMTP sink for email features; messages are viewable via UI | SMTP: `localhost:1025`, UI: http://localhost:8025 |

After copying `.env.example`, the defaults already point to these containers (JWT secret, AWS credentials, etc.). Override the values if you need to target real services.

### API Endpoints
- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- API Docs: http://localhost:8080/v3/api-docs
- Health Check: http://localhost:8080/actuator/health

### 🔐 JWT Authentication
All `/api/v1/**` endpoints require JWT authentication (except `/api/v1/auth/**`).

**Quick test:**
```bash
./quickstart-jwt.sh
```

**Documentation:**
- 📖 Full guide: [JWT_AUTHENTICATION_GUIDE.md](JWT_AUTHENTICATION_GUIDE.md)
- ⚡ Quick reference: [JWT_QUICK_REFERENCE.md](JWT_QUICK_REFERENCE.md)
- 📝 Implementation: [JWT_IMPLEMENTATION_SUMMARY.md](JWT_IMPLEMENTATION_SUMMARY.md)

**Basic usage:**
```bash
# Register
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username": "john", "email": "john@test.com", "password": "pass123"}'

# Use the returned accessToken
curl -X GET http://localhost:8080/api/v1/users \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

## Deploy to AWS (Simple)

This repository includes a simple GitHub Actions workflow that builds the Docker image directly on your EC2 instance and runs it.

Prerequisites on EC2:
- Docker installed and running
- Your EC2 user is in the `docker` group (no sudo needed)

Required GitHub Secrets:
- `EC2_HOST` – Public IP/DNS of the instance
- `EC2_USER` – SSH user (e.g., `ubuntu` for Ubuntu AMIs)
- `EC2_SSH_PRIVATE_KEY` – Private key contents for SSH (BEGIN/END lines included)
- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`
- `JWT_SECRET` – Secret key for JWT token signing (generate with `openssl rand -base64 64`)

Trigger a deploy by pushing to `main` or running the workflow manually.

### Managing your deployment

Create `.ec2-config`:
```bash
cp .ec2-config.example .ec2-config
# Edit with your values
```
Use the helper script:
```bash
./manage-deployment.sh info
./manage-deployment.sh status
./manage-deployment.sh logs
./manage-deployment.sh health
```

## MCP Agent

`com.runwithme.runwithme.api.mcp` klasörü, çağıran kullanıcının yetkileriyle çalışan ve JSONPlaceholder + Gemini üstünde örnek bir akış oluşturan general-purpose MCP ajanını içerir. Çalışma sırası:

1. `McpPromptRouter` yalnızca izin verdiğiniz endpoint/fonksiyon listesini (HTTP method, path, açıklama) tutar.
2. `GeminiClient.selectRoute` bu listeyi LLM'e function-calling formatında verip, kullanıcının prompt'una göre hangi rota seçileceğini modelden ister.
3. `McpPromptRouter` seçilen route adını whitelist'te doğrular; whitelist dışı seçimler otomatik reddedilir (policy enforcement).
4. `McpExternalApiClient` doğrulanan rotayı, çağıran kişinin `Authorization` başlığını aynen forward ederek çağırır.
5. `GeminiClient.generateAnswer` prompt + API cevabını `gemini-1.5-flash` modeline gönderir ve kısa aksiyon önerisi üretir.

### Yapılandırma

`.env` dosyanıza aşağıdaki değerleri ekleyin (örnekler `.env.example` içinde de var):
```
MCP_EXTERNAL_API_BASE_URL=https://jsonplaceholder.typicode.com
MCP_GEMINI_MODEL=gemini-1.5-flash
MCP_GEMINI_API_KEY=YOUR_API_KEY
```
`MCP_EXTERNAL_API_BASE_URL` zincirdeki tüm rotalar için temel URL'dir; varsayılan rotalar JSONPlaceholder'ı hedefler. Function-calling adımı da Gemini'ye bağlı olduğu için `MCP_GEMINI_API_KEY` boş bırakılırsa rota seçimi yapılamaz ve ajan `success=false` döner.

### Kullanım

Tüm `/api/v1/mcp/**` uç noktaları JWT ister. Uygulamayı çalıştırdıktan sonra şu isteği deneyin:
```bash
curl -X POST http://localhost:8080/api/v1/mcp/run \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -d '{
    "prompt": "Todo kaydini goster ve yorumlar hakkinda ipucu ver"
  }'
```

Ajan prompt'u Gemini üzerinden analiz eder ve seçimi `routeDecisionReason` alanında açıklar. Yanıtta `success`, `routeName`, `requestedUrl`, `apiBody`, `llmMessage`, `routeDecisionReason`, `resolvedArguments`, `starterUserId`, `error` alanları bulunur. Backend sohbeti başlatan kullanıcının kimliğini otomatik olarak `starterUserId` şeklinde prompt'a ekler; böylece kullanıcı “profilim” gibi ifadeler kullandığında LLM bu kimliği kullanarak doğru rotayı seçebilir. Model listedeki rotalardan birini seçemezse ya da whitelist doğrulamasını geçemezse `success=false` ve `error` mesajı görürsünüz; hiçbir zaman whitelist dışındaki endpoint'ler çağrılmaz.

Varsayılan fonksiyon listesini `McpPromptRouter` sınıfında bulabilir, yeni rotalar ekleyebilir ya da açıklamaları güncelleyebilirsiniz. Parametreli endpointler için `pathTemplate` alanında placeholder (ör. `api/v1/users/username/{username}`) tanımlayıp `parameters` listesine `username` gibi anahtarları ekleyebilirsiniz. Gemini'ye gönderilen function-calling prompt, bu parametreleri JSON içindeki `arguments` alanında döndürmesini ister. Backend, gelen `arguments` değerlerini URL encode ederek template içinde değiştirir; eksik parametre varsa politika gereği çağrı yapılmaz.

Örnek: kullanıcı ismine göre bilgi çekmek için aşağıdaki prompt'u yollayabilirsiniz:
```bash
curl -X POST http://localhost:8080/api/v1/mcp/run \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -d '{
    "prompt": "minaaa kullanicisinin profilini getir"
  }'
```
LLM `Kullanici Ismiyle Kullanici Bilgisi` rotasını seçer, `arguments.username=minaaa` olarak döner ve sonuç `requestedUrl` alanında `.../api/v1/users/username/minaaa` şeklinde görünür.

### Test Etme

1. `.env` içindeki MCP değişkenlerini ve JWT ayarlarınızı doldurun, ardından uygulamayı JWT ile giriş yapabileceğiniz şekilde çalıştırın.
2. `./gradlew clean test` komutu ile birim testleri ve format kontrollerini çalıştırın (JDK 17+ gerekir).
3. MCP ajanını manuel doğrulamak için yetkili bir kullanıcı token'ı üreterek yukarıdaki `curl` komutunu çalıştırın. `success=false` durumları için hata mesajının beklediğiniz senaryolarla eşleştiğini kontrol edin.

## CI

Two CI workflows exist for Java build/tests. The main one is `.github/workflows/ci.yml`.
