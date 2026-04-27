# AI Intelligent Learning Assistant System

**语言**: [中文](README.md) | English

> A full-stack AI learning loop built around material management -> RAG retrieval -> AI summary and question generation -> online practice -> mistake review -> learning analytics -> agent-based study assistant.  
> This project is suitable as a reference case for **AI application practice, RAG engineering, agent tool calling, graduation projects, course projects, and portfolio projects**.

![AI Intelligent Learning Assistant System Social Preview](docs/images/social-preview.png)

## Contact

If you have questions or suggestions about this project, you can email: `3111471949@qq.com`

> Note: advertising or promotional messages may not receive a reply. Please include "AI Learning System + your purpose" in the email subject.

## Live Demo

Note: users in China may need a VPN to access the online demo.

- Frontend Demo: [https://ai-intelligent-learning-assistant-s.vercel.app](https://ai-intelligent-learning-assistant-s.vercel.app)
- Backend Health Check: [https://backend-production-d1f3.up.railway.app/api/health](https://backend-production-d1f3.up.railway.app/api/health)
- GitHub Repository: [https://github.com/Nyx-Amanises/AI-Intelligent-Learning-Assistant-System](https://github.com/Nyx-Amanises/AI-Intelligent-Learning-Assistant-System)

> The production environment is deployed with Vercel and Railway. The backend may take a few seconds to respond on the first request if it is cold-starting.

## Screenshots

| Home / Learning Overview | Agent Study Assistant |
| --- | --- |
| ![主页](docs/images/home.png) | ![Agent Study Assistant](docs/images/agent.png) |

| Material Management | RAG Evaluation |
| --- | --- |
| ![Material Management](docs/images/materials.png) | ![RAG Evaluation](docs/images/rag-eval.png) |

| Learning Analytics | AI Configuration |
| --- | --- |
| ![Learning Analytics](docs/images/analytics.png) | ![AI Configuration](docs/images/ai-config.png) |

## Core Features

### Material-Driven Learning Loop

- Upload or create learning materials, with support for TXT, PDF, DOCX, and other parsed content.
- Generate AI summaries, review outlines, and practice questions from materials.
- Support online practice, automatic scoring, mistake collection, and knowledge mastery statistics.
- Summarize accuracy, mistake count, weak knowledge points, and practice trends in the learning analytics dashboard.

### RAG and Vector Retrieval

- Split material content into chunks and generate embeddings.
- Store material vectors in Qdrant and support semantic retrieval.
- Preview RAG retrieval results with similarity score, page number, paragraph, and section metadata.
- Manage RAG evaluation datasets and calculate Hit@K, Recall@K, MRR, and latency.
- Import the CMRC2018 dataset for batch retrieval quality evaluation.

### Agent Study Assistant

- Support general study conversations and SSE streaming responses.
- Support session lists, new conversations, conversation deletion, and tool-call traces.
- Query system context such as materials, question sets, practice records, and task status.
- New conversations do not automatically bind to materials by default. Users can explicitly specify a material title or ID in the message.
- Display a notice when the current AI configuration is running in mock mode.

### AI Configuration and Task Center

- Configure chat models and embedding models separately.
- Support OpenAI-compatible APIs, DeepSeek, Doubao Ark, and similar compatible providers.
- Support mock mode for demonstrating the workflow without a real model API key.
- Route long-running operations such as AI summaries, AI question generation, short-answer grading, and embedding into the task center.
- Track task status, view details, retry failures, redispatch tasks, and delete task records.

## Tech Stack

| Layer | Technologies |
| --- | --- |
| Frontend | Vue 3, TypeScript, Vite, Pinia, Vue Router, Element Plus, Axios |
| Backend | Spring Boot 3.3, Java 17, MyBatis Plus, JWT, Bean Validation |
| Database | MySQL 8, Redis |
| RAG | Qdrant, Embedding API, material chunking, Hit@K / Recall@K / MRR evaluation |
| AI | OpenAI-compatible Chat API, SSE streaming, mock mode |
|deployment| Docker Compose, Vercel, Railway |

## Project Structure

```text
AI-Intelligent-Learning-Assistant-System
├─ backend/                  # Spring Boot backend
│  ├─ src/main/java/          # Business code
│  ├─ src/main/resources/     # Configuration files
│  └─ Dockerfile
├─ frontend/                 # Vue 3 frontend
│  ├─ src/api/                # API clients
│  ├─ src/components/         # Shared components
│  ├─ src/views/              # Page views
│  └─ Dockerfile
├─ db/                        # Database schema and migration scripts
├─ docker/mysql/init/         # MySQL container initialization scripts
├─ docs/images/               # README screenshots and social preview
├─ runtime/                   # AI runtime config, sensitive values are not committed by default
├─ docker-compose.yml         # One-command local startup
├─ vercel.json                # Vercel frontend deployment config
├─ README.md                  # Chinese README
└─ README.en.md               # English README
```

## Quick Start

### Option 1: Docker Compose

1. Prepare environment variables:

```powershell
Copy-Item .env.example .env
```

2. Start all services:

```powershell
docker compose up -d --build
```

3. Open services:

- Frontend: http://localhost:5173
- Backend: http://localhost:8083
- MySQL: localhost:3307
- Redis: localhost:6379
- Qdrant: http://localhost:6333

4. View logs:

```powershell
docker compose logs -f backend
docker compose logs -f frontend
```

5. Stop services:

```powershell
docker compose down
```

### Option 2: Manual Local Startup

Backend:

```powershell
Set-Location backend
mvn spring-boot:run
```

Frontend:

```powershell
Set-Location frontend
npm install
npm run dev
```

If vector retrieval is needed, start Qdrant as well:

```powershell
docker run -d --name qdrant -p 6333:6333 -p 6334:6334 -v qdrant_storage:/qdrant/storage qdrant/qdrant
```

## Core Configuration

The backend supports overriding configuration through environment variables:

| Variable |描述|
| --- | --- |
| `SPRING_DATASOURCE_URL` | MySQL connection URL |
| `SPRING_DATASOURCE_USERNAME` | MySQL username |
| `SPRING_DATASOURCE_PASSWORD` | MySQL password |
| `SPRING_REDIS_HOST` | Redis host |
| `APP_AI_ENABLED` | Whether AI capabilities are enabled |
| `APP_AI_MOCK_MODE` | Whether mock mode is enabled |
| `APP_AI_CHAT_PROVIDER_TYPE` | Chat model provider |
| `APP_AI_BASE_URL` | Chat API base URL |
| `APP_AI_CHAT_PATH` | Chat API path |
| `APP_AI_API_KEY` | Chat API key |
| `APP_AI_DEFAULT_MODEL` | Default chat model |
| `APP_AI_EMBEDDING_BASE_URL` | Embedding API base URL |
| `APP_AI_EMBEDDING_API_KEY` | Embedding API key |
| `APP_AI_DEFAULT_EMBEDDING_MODEL` | Default embedding model |
| `APP_QDRANT_BASE_URL` | Qdrant base URL |
| `APP_QDRANT_COLLECTION_NAME` | Qdrant collection name |
| `APP_JWT_SECRET` | JWT secret |

Runtime AI configuration saved from the AI configuration page is written to:

```text
runtime/ai-config.json
```

## Common APIs

| Module | Example APIs |
| --- | --- |
| Auth | `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/user/profile` |
| Materials | `POST /api/material/upload`, `GET /api/material/page`, `POST /api/material/{id}/parse` |
| AI Tasks | `POST /api/ai/tasks/material/{materialId}/summary`, `POST /api/ai/tasks/material/{materialId}/question-set` |
| Practice | `POST /api/practice/start`, `POST /api/practice/submit`, `GET /api/practice/page` |
| Analytics | `GET /api/wrong-questions/page`, `GET /api/knowledge-mastery/overview`, `GET /api/learning-analytics/overview` |
| RAG | `GET /api/rag/material/{materialId}/retrieve-preview`, `POST /api/rag-eval/datasets/{datasetId}/run` |
| Agent | `POST /api/assistant/sessions`, `POST /api/assistant/sessions/{sessionId}/messages/stream` |

## Deployment Notes

Current production deployment:

- Frontend: Vercel
- Backend: Railway
- Database and dependencies: Railway / external managed services

Frontend production build:

```powershell
Set-Location frontend
npm run build
```

Backend production build:

```powershell
Set-Location backend
mvn -DskipTests package
```

Vercel uses the root-level `vercel.json`:

```json
{
  "installCommand": "cd frontend && npm ci",
  "buildCommand": "cd frontend && npm run build",
  "outputDirectory": "frontend/dist"
}
```

For Railway backend deployment, make sure the service root points to `backend` and configure MySQL, Redis, Qdrant, AI provider, and JWT environment variables.

## Roadmap

- Add more complete agent tool orchestration and permission control.
- Add hybrid retrieval, reranking models, and RAG A/B testing.
- Add study plans, spaced repetition, and personalized recommendations.
- Add model call cost statistics and request logs.
- Add GitHub Actions for automated build and deployment.

## License

This project is intended for learning, course projects, and portfolio showcases. For commercial use, please verify the licenses of all dependencies and model providers first.
