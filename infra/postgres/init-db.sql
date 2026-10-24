-- Applied only on a fresh PostgreSQL volume. Existing legacy databases are not dropped.
\getenv db_owner POSTGRES_USER
CREATE DATABASE vishop_iam OWNER :"db_owner";
CREATE DATABASE vishop_audit OWNER :"db_owner";

\connect vishop_iam
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

\connect vishop_audit
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
