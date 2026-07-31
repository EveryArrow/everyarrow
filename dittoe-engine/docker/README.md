# DITTOE Docker Test Environment

This directory contains Docker configuration for testing DITTOE data pipelines.

## Quick Start

```bash
# Create docker/.env from docker/.env.example and set local-only passwords first.

# Start all containers
docker compose up -d

# Check status
docker compose ps

# View logs
docker compose logs -f

# Stop containers
docker compose down

# Stop and remove volumes (reset data)
docker compose down -v
```

## Matrix Overlay

The matrix test overlay adds local substitutes for cloud-style file I/O and extra canonical seed data:

```bash
docker compose -f docker-compose.yml -f docker-compose.matrix.yml up -d

# Reset all matrix and database state
docker compose -f docker-compose.yml -f docker-compose.matrix.yml down -v
```

From the repository root, generate or run matrix PMDs with:

```bash
python3 tests/matrix/matrix.py generate --profile smoke
python3 tests/matrix/matrix.py run --generate --package --case local-file-to-s3/canonical-smoke
```

## Containers

| Container | Database | Port | User | Password | DB Name |
|-----------|----------|------|------|----------|---------|
| postgres-source | PostgreSQL 16 | 5433 | dittoe | `POSTGRES_PASSWORD` from `.env` | adventureworks |
| postgres-target | PostgreSQL 16 | 5434 | dittoe | `POSTGRES_PASSWORD` from `.env` | adventureworks |
| mysql-source | MySQL 8.0 | 3307 | dittoe | `MYSQL_PASSWORD` from `.env` | adventureworks |
| mysql-target | MySQL 8.0 | 3308 | dittoe | `MYSQL_PASSWORD` from `.env` | adventureworks |

The matrix overlay also exposes MinIO on 9000/9001, fake-gcs-server on 4443, Azurite Blob on 10000, FTP on 2121, SFTP on 2223, and MSSQL on 14333.

## Test Data

### PostgreSQL Source
- `production.product` - 100 rows (bike parts, accessories)
- `production.person` - 20 rows (employees, contacts)
- `production.department` - 16 rows (organizational units)

### MySQL Source
- `product` - 50 rows
- `person` - 10 rows
- `department` - 10 rows

Target databases have the same schema but no data.

## Connection Examples

### PostgreSQL
```bash
# Source
psql -h localhost -p 5433 -U dittoe -d adventureworks
# Password: POSTGRES_PASSWORD from docker/.env

# Target
psql -h localhost -p 5434 -U dittoe -d adventureworks
```

### MySQL
```bash
# Source
mysql -h 127.0.0.1 -P 3307 -u dittoe -p adventureworks

# Target
mysql -h 127.0.0.1 -P 3308 -u dittoe -p adventureworks
```

## Verify Row Counts

```bash
# PostgreSQL source
docker exec dittoe-postgres-source psql -U dittoe -d adventureworks \
  -c "SELECT COUNT(*) as products FROM production.product"

# PostgreSQL target (should be 0)
docker exec dittoe-postgres-target psql -U dittoe -d adventureworks \
  -c "SELECT COUNT(*) as products FROM production.product"

# MySQL source
docker compose exec mysql-source sh -lc 'mysql -udittoe -p"$MYSQL_PASSWORD" adventureworks -e "SELECT COUNT(*) as products FROM product"'
```
