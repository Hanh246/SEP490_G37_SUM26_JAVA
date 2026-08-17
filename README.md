# ComiVerse API (Backend Core)

![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.0-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white) ![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-4169E1?style=for-the-badge&logo=postgresql&logoColor=white) ![Java](https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)

This repository contains the backend core of **ComiVerse**, a premium global comic and manga platform. It provides a robust, scalable RESTful API that handles content delivery, user authentication, financial ledgers, and real-time community engagement.

---

## 🏗️ Architecture & Technologies

- **Core Framework**: Java 17, Spring Boot 3
- **Security**: Spring Security, JWT (JSON Web Tokens), OAuth2
- **Database Layer**: PostgreSQL, Spring Data JPA, Hibernate, pgvector (Vector DB Indexing for AI/Search capabilities).
- **Real-time Communication**: Spring WebSocket, STOMP protocol, Redis (Session Management / PubSub).
- **External Integrations**:
  - **Cloudinary**: High-performance image storage and delivery for comic chapters.
  - **Stripe API**: Automated financial ledger, payments, and payouts for Authors and Translators.
  - **Firebase Admin**: Push notifications mechanism.
  - **Resend / SendGrid**: Automated transactional emails.
- **Documentation**: Swagger / OpenAPI 3.0

---

## 🌟 Core System Workflows

### 🔐 1. Authentication & RBAC (Role-Based Access Control)
**Main Workflow:** Login -> Verify JWT -> Enforce Access Control.
- 6-level hierarchical security system (Reader, Author, Translator, Project Leader, Moderator, Admin).
- OAuth2 integration enables quick social login via Google/Facebook.
- Token-based authentication equipped with a secure Refresh Token mechanism.

### 💰 2. Financial Ledger & Monetization Engine
**Main Workflow:** User reads/purchases -> Calculate Revenue (View-unit) -> Store in Ledger -> Stripe Payout Request.
- **Micro-transaction Tracking**: Detailed tracking of user reads to calculate and split revenue for Authors based on a dynamic View-unit rate.
- **Translator Payout System**: Automated compensation calculation for Translators based on the **number of translated pages** and an assigned **Responsibility factor**.
- **Stripe Integration**: Creators and Translators can request direct bank withdrawals via Stripe Connect.

### 📚 3. Content Delivery & Cloudinary Storage
**Main Workflow:** Author uploads -> BE processes -> Uploads to Cloudinary -> Distributes to Frontend.
- Automated batch image conversion and compression before pushing to the Cloudinary CDN.
- Manages publication lifecycles (Draft, Pending Review, Ongoing, Completed, Hiatus).
- Extremely fast API response times utilizing Redis Caching to power the Frontend's zero-latency preloading feature.

### ⚖️ 4. Moderation & Strike System
**Main Workflow:** Receive Report -> Store in Database -> Process Evidence -> Ban/Mute/Takedown.
- Intercepts and categorizes user reports (e.g., Violence, Copyright Violations) into a dedicated Review Queue.
- "Strike" warning system. Automatically mutes or bans accounts that reach the strike threshold.
- Automated warning and penalty emails sent via JavaMailSender / Resend.

### 🌐 5. Translation Workspace (Kanban Logic)
**Main Workflow:** Task Delegation -> Update Kanban Status -> Split-screen Translation -> Merge.
- Provides specific RESTful APIs to power the Kanban board (Drag-and-drop tasks from To Do to In Progress).
- Implements database row-locking and concurrency control to prevent multiple Translators from editing the same comic page simultaneously.

---

## 🚀 Setup Instructions

1. **Clone the repository:**
   ```bash
   git clone https://github.com/Hiepbq2003/SEP490_G37_SUM26_JAVA.git
   cd SEP490_G37_SUM26_JAVA
   ```

2. **Database Setup:**
   Ensure PostgreSQL is running locally on port 5432. Create a database named `comiverse`.
   
3. **Environment Variables (`.env`):**
   Copy `.env.example` to `.env` and fill in your local credentials:
   ```env
   DB_URL=jdbc:postgresql://localhost:5432/comiverse
   DB_USER=postgres
   DB_PASSWORD=your_password
   JWT_SECRET=your_super_secret_jwt_key
   CLOUDINARY_URL=cloudinary://...
   STRIPE_API_KEY=sk_test_...
   ```

4. **Run the Application:**
   ```bash
   mvn spring-boot:run
   ```

5. **API Documentation:**
   Once running, access the interactive Swagger UI at:
   `http://localhost:8080/swagger-ui.html`

---

## 🔑 Database Seeder (Test Accounts)

When starting the application for the first time in a non-production profile, the `DbInitializer` will automatically seed the database with testing accounts for all roles (Password for all except Reader is `staff123`):
- `admin` (System Administrator)
- `moderator1` (Moderator)
- `author1` (Creator)
- `translator1` (Translator)
- `projectleader1` (Project Leader)
- `reader1` (Reader - password: `reader123`)
