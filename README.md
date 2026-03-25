# SmartCart Ecommerce API

SmartCart Ecommerce is a Spring Boot backend for core e-commerce workflows: authentication, category and product management, image upload, and shopping cart operations.

## Features

- JWT-based authentication with cookie session handling
- Role model with seeded `ROLE_USER`, `ROLE_SELLER`, and `ROLE_ADMIN`
- Category CRUD APIs with pagination and sorting
- Product CRUD APIs with category filtering and keyword search
- Product image upload and static image hosting under `/images/**`
- User cart management (add, update quantity, remove, view cart)
- Global exception handling with consistent API error responses

## Tech Stack

- Java 21
- Spring Boot 4.0.2
- Spring Web MVC
- Spring Security + JWT (`jjwt`)
- Spring Data JPA
- PostgreSQL
- Maven Wrapper (`mvnw`, `mvnw.cmd`)

## Project Structure

```text
src/main/java/com/psd/smartcart_ecommerce
|- config
|- controllers
|- exceptions
|- models
|- payload
|- repositories
|- security
|- services
|- util

src/main/resources
|- application.properties
```

## Prerequisites

- JDK 21
- PostgreSQL running locally
- Git + terminal (PowerShell, CMD, or bash)

## Configuration

Default config is in `src/main/resources/application.properties`.

Important properties:

- `spring.datasource.url`
- `spring.datasource.username`
- `spring.datasource.password`
- `spring.app.jwtSecret`
- `spring.app.jwtExpirationMs`
- `spring.ecom.app.jwtCookieName`
- `frontend.url`
- `project.image`
- `image.base.url`

For local development, make sure PostgreSQL credentials and DB URL match your environment.

## Run Locally

### Windows (PowerShell)

```powershell
.\mvnw.cmd spring-boot:run
```

### macOS/Linux

```bash
./mvnw spring-boot:run
```

The API starts at:

- `http://localhost:8080`

## Seeded Users (Created at Startup)

| Username | Password  | Roles |
| --- | --- | --- |
| `user1` | `password1` | `ROLE_USER` |
| `seller1` | `password2` | `ROLE_SELLER` |
| `admin` | `adminPass` | `ROLE_USER`, `ROLE_SELLER`, `ROLE_ADMIN` |

## Authentication Flow

1. Call `POST /api/auth/signin` with username/password.
2. Backend returns a JWT and sets cookie `springBootEcom` (path `/api`).
3. Send this cookie in subsequent protected requests.
4. Call `POST /api/auth/signout` to clear auth cookie.

Example login:

```bash
curl -i -c cookies.txt -X POST http://localhost:8080/api/auth/signin \
  -H "Content-Type: application/json" \
  -d '{"username":"user1","password":"password1"}'
```

Example protected cart call:

```bash
curl -b cookies.txt http://localhost:8080/api/carts/users/cart
```

## API Endpoints

### Auth

- `POST /api/auth/signup`
- `POST /api/auth/signin`
- `POST /api/auth/signout`
- `GET /api/auth/username`
- `GET /api/auth/user`

Signup body example:

```json
{
  "username": "newuser",
  "email": "newuser@example.com",
  "password": "strongpass123",
  "role": ["user"]
}
```

Supported role strings: `admin`, `seller`, `user`.

### Categories

- `GET /api/public/categories`
- `POST /api/public/categories`
- `PUT /api/public/categories/{categoryId}`
- `DELETE /api/admin/categories/{categoryId}`

Category body example:

```json
{
  "categoryName": "Electronics"
}
```

Pagination/sort query params (where supported):

- `pageNumber` (default `0`)
- `pageSize` (default `50`)
- `sortBy` (default category: `categoryId`, product: `productId`)
- `sortOrder` (`asc` or `desc`, default `asc`)

### Products

- `POST /api/admin/categories/{categoryId}/products`
- `GET /api/public/products`
- `GET /api/public/categories/{categoryId}/products`
- `GET /api/public/products/keyword/{keyword}`
- `PUT /api/admin/products/{productId}`
- `DELETE /api/admin/products/{productId}`
- `PUT /api/products/{productId}/image` (multipart form-data)

Product body example:

```json
{
  "productName": "Wireless Mouse",
  "description": "Ergonomic mouse with 2.4GHz receiver",
  "quantity": 20,
  "price": 25.0,
  "discount": 10.0
}
```

Image upload example:

```bash
curl -X PUT -b cookies.txt \
  -F "image=@/path/to/product.jpg" \
  http://localhost:8080/api/products/1/image
```

### Cart

- `POST /api/carts/products/{productId}/quantity/{quantity}`
- `GET /api/carts`
- `GET /api/carts/users/cart`
- `PUT /api/cart/products/{productId}/quantity/{operation}`
- `DELETE /api/carts/{cartId}/product/{productId}`

`operation` in cart update supports values like `delete` (decrement) or any other value (increment).

## Error Response Format

Validation errors:

```json
{
  "fieldName": "validation message"
}
```

Business/resource errors:

```json
{
  "message": "Readable error message",
  "status": false
}
```

## Security Notes (Current Behavior)

- `WebSecurityConfig` currently permits `"/api/admin/**"` in the filter chain.
- JWT cookie is currently created with `httpOnly(false)`.

These settings are suitable for development/testing but should be tightened before production deployment.

## CORS

CORS allows:

- `http://localhost:3000`
- `frontend.url` from properties (default `http://localhost:5173/`)

## Testing

Run tests:

```bash
./mvnw test
```

Current automated test coverage is minimal (`contextLoads`).
