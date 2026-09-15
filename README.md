# Gusto — Minimalist Recipe Mobile & Backend Platform

**Gusto** é uma plataforma de culinária e receitas focada em minimalismo, elegância tipográfica, transições a 60fps e experiência de usuário sem atrito.

---

## 📁 Estrutura do Monorepo

```
app/
├── backend/                  # Next.js 15 Serverless API (Vercel Ready)
│   ├── prisma/
│   │   └── schema.prisma     # Modelagem Relacional PostgreSQL (Neon.tech)
│   ├── src/
│   │   ├── app/api/
│   │   │   ├── auth/         # Register, Login, Refresh, Forgot/Reset OTP
│   │   │   └── recipes/      # CRUD, Feed Unificado & TheMealDB Adapter
│   │   └── lib/
│   │       ├── auth.ts       # Hash, JWT & Refresh Token Rotation
│   │       ├── mealdb.ts     # TheMealDB API Normalizer
│   │       ├── prisma.ts     # Singleton Client com Pooling
│   │       └── rate-limit.ts # Rate Limiter contra força bruta
│   ├── .env.example
│   ├── package.json
│   └── tsconfig.json
│
└── android/                  # Android Nativo (Kotlin + Jetpack Compose)
    ├── gradle/
    │   ├── libs.versions.toml# Version Catalog Gradle
    │   └── wrapper/
    ├── app/
    │   ├── src/main/
    │   │   ├── AndroidManifest.xml
    │   │   └── java/com/gusto/app/
    │   │       ├── data/     # Retrofit API, Models, Repositories, Interceptor
    │   │       ├── ui/
    │   │       │   ├── screens/ # Auth, Feed, Detail (Interativo), Create
    │   │       │   └── theme/   # Light, Dark, OLED True Black (#000000)
    │   │       └── MainActivity.kt
    │   └── build.gradle.kts
    ├── build.gradle.kts
    └── settings.gradle.kts
```

---

## 🗄️ Modelagem de Banco de Dados (Neon PostgreSQL)

Gerenciado via **Prisma ORM**, otimizado para o **Neon Connection Pooler**:

- **User**: Gerenciamento de credenciais, hash de senha e preferência reativa de tema.
- **RefreshToken**: Rotação com revogação e prevenção de reuso malicioso.
- **PasswordResetOtp**: Códigos numéricos de 6 dígitos descartáveis, criptografados e com janela de expiração de 10 minutos.
- **Recipe**: Entidade central com tempo de preparo, rendimento, categoria, imagem e flags de autoria/TheMealDB.
- **RecipeIngredient**: Tabela relacional com quantidade precisa (float) e unidade de medida.
- **RecipeStep**: Passos ordenados sequencialmente.

---

## 🚀 Como Executar o Backend

```bash
cd backend
npm install
cp .env.example .env
# Adicione a URL do Neon PostgreSQL em DATABASE_URL
npx prisma db push
npm run dev
```

---

## 📱 Como Compilar o App Android Localmente via Gradle

Certifique-se de ter o JDK 17 configurado:

```bash
cd android

# Compilação de Debug
./gradlew assembleDebug

# Compilação de Release
./gradlew assembleRelease
```
O APK gerado estará em `android/app/build/outputs/apk/release/app-release.apk`.
