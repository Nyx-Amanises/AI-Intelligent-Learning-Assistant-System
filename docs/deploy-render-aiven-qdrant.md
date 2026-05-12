# Free Cloud Deployment: Render + Aiven + Qdrant Cloud

This guide migrates the backend away from Railway while keeping the Vercel frontend.

## Target Architecture

- Frontend: Vercel
- Backend: Render Web Service, Docker build from `backend/Dockerfile`
- MySQL: Aiven MySQL Free
- Redis/Valkey: Aiven Valkey Free or Upstash Redis
- Vector store: Qdrant Cloud Free

## 1. Create Aiven MySQL

Create a MySQL service on Aiven, then copy:

- Host
- Port
- Database name
- User
- Password

Render needs these environment variables:

```text
SPRING_DATASOURCE_URL=jdbc:mysql://<AIVEN_MYSQL_HOST>:<PORT>/<DATABASE>?sslMode=REQUIRED&allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
SPRING_DATASOURCE_USERNAME=<AIVEN_MYSQL_USER>
SPRING_DATASOURCE_PASSWORD=<AIVEN_MYSQL_PASSWORD>
```

For a fresh database, initialize schema with:

```text
db/ai_learning_assistant.sql
db/migrations/2026-04-30_create_ai_config_table.sql
```

If data is migrated from an old Railway database, import the dump first, then run only missing migration files. At minimum, make sure `ai_config` exists.

## 2. Create Redis/Valkey

For Aiven Valkey or Upstash Redis, copy host, port, and password.

Render variables:

```text
SPRING_REDIS_HOST=<REDIS_HOST>
SPRING_REDIS_PORT=<REDIS_TLS_PORT>
SPRING_REDIS_PASSWORD=<REDIS_PASSWORD>
SPRING_REDIS_DATABASE=0
SPRING_REDIS_SSL_ENABLED=true
```

If the provider gives a non-TLS port, set `SPRING_REDIS_SSL_ENABLED=false`.

## 3. Create Qdrant Cloud

Create a free Qdrant cluster, then copy:

- Cluster URL
- API Key

Render variables:

```text
APP_QDRANT_ENABLED=true
APP_QDRANT_BASE_URL=https://<QDRANT_CLUSTER_HOST>
APP_QDRANT_API_KEY=<QDRANT_API_KEY>
APP_QDRANT_COLLECTION_NAME=material_segment_vectors
```

The backend creates the collection lazily when embedding/retrieval runs.

## 4. Deploy Backend On Render

Recommended manual setup:

```text
Service Type: Web Service
Repository: Nyx-Amanises/AI-Intelligent-Learning-Assistant-System
Runtime: Docker
Root Directory: backend
Dockerfile Path: Dockerfile
Plan: Free
```

Or use the repository `render.yaml` Blueprint.

Required Render variables:

```text
APP_CORS_ALLOWED_ORIGINS=https://ai-intelligent-learning-assistant-s.vercel.app
APP_FILE_UPLOAD_DIR=/tmp/ai-learning-uploads
APP_AI_CONFIG_FILE=/tmp/ai-config.json
APP_AI_ENABLED=true
APP_AI_MOCK_MODE=true
APP_JWT_SECRET=<random-long-secret>

SPRING_DATASOURCE_URL=<see section 1>
SPRING_DATASOURCE_USERNAME=<see section 1>
SPRING_DATASOURCE_PASSWORD=<see section 1>

SPRING_REDIS_HOST=<see section 2>
SPRING_REDIS_PORT=<see section 2>
SPRING_REDIS_PASSWORD=<see section 2>
SPRING_REDIS_SSL_ENABLED=true

APP_QDRANT_ENABLED=true
APP_QDRANT_BASE_URL=<see section 3>
APP_QDRANT_API_KEY=<see section 3>
APP_QDRANT_COLLECTION_NAME=material_segment_vectors
```

AI keys do not need to be set in Render if the `admin` user configures the shared key in the app.

## 5. Update Vercel

After Render deploys successfully, copy the Render backend URL, then set this in Vercel:

```text
VITE_API_BASE_URL=https://<RENDER_BACKEND_DOMAIN>/api
```

Redeploy Vercel after changing the variable.

## 6. Post-deploy Checks

1. Open the backend health smoke check:

```text
https://<RENDER_BACKEND_DOMAIN>/api/ai/config
```

Unauthenticated requests should return an auth error, which still proves the backend is reachable.

2. Open the frontend, log in as `admin`.
3. Go to AI config, switch to shared config, save the shared API key.
4. Upload a small document and confirm parse -> embedding -> RAG retrieval.

## Notes

Render free web services sleep after inactivity. First request after sleep can be slow.

Render free web service storage is not persistent. This project should treat uploads as temporary input files and rely on parsed database records plus Qdrant vectors for long-lived data.
