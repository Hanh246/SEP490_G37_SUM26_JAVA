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
**Luồng chính:** Đăng nhập -> Verify JWT -> Phân quyền truy cập.
- Hệ thống bảo mật 6 cấp độ quyền (Reader, Author, Translator, Project Leader, Moderator, Admin).
- OAuth2 integration cho phép đăng nhập nhanh qua Google/Facebook.
- Token-based authentication với cơ chế Refresh Token bảo mật.

### 💰 2. Financial Ledger & Monetization Engine
**Luồng chính:** Người dùng đọc truyện / Trả phí -> Tính toán Revenue (View-unit) -> Lưu Ledger -> Request Payout qua Stripe.
- **Micro-transaction Tracking**: Theo dõi chi tiết từng lượt đọc của người dùng để chia nhỏ doanh thu (revenue split) cho Author dựa trên View-unit rate.
- **Translator Payout System**: Tự động tính tiền công cho Translator dựa trên **số trang truyện đã dịch** và **hệ số trách nhiệm (Responsibility factor)**.
- **Stripe Integration**: Cho phép Creator/Translator yêu cầu rút tiền (Withdrawal) và chuyển thẳng tiền thật về tài khoản ngân hàng thông qua Stripe Connect.

### 📚 3. Content Delivery & Cloudinary Storage
**Luồng chính:** Author upload -> BE process -> Upload Cloudinary -> Phân phối Frontend.
- Chuyển đổi và nén hàng loạt ảnh tự động trước khi đẩy lên Cloudinary CDN.
- Quản lý trạng thái xuất bản (Draft, Pending Review, Ongoing, Completed, Hiatus).
- API phản hồi cực nhanh với cơ chế Caching (Redis) để phục vụ cho tính năng Zero-latency preloading của Frontend.

### ⚖️ 4. Moderation & Strike System
**Luồng chính:** Nhận Report -> Lưu Database -> Xử lý bằng chứng -> Ban/Mute/Takedown.
- Hệ thống bắt và phân loại các báo cáo (Bạo lực, Bản quyền) đưa vào Review Queue.
- Cơ chế "Cảnh cáo" (Strikes). Tự động khóa mõm (Mute) chat hoặc cấm truy cập (Ban) nếu User đạt ngưỡng cảnh cáo.
- Gửi Email cảnh báo tự động thông qua JavaMailSender / Resend.

### 🌐 5. Translation Workspace (Kanban Logic)
**Luồng chính:** Giao việc -> Cập nhật trạng thái Kanban -> Split-screen Translation -> Merge.
- Cung cấp các API RESTful đặc thù cho bảng Kanban (Kéo thả Task từ To Do sang In Progress).
- Xử lý lock row database để tránh việc 2 Translator cùng chỉnh sửa 1 trang truyện đồng thời (Concurrency control).

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
