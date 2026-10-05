-- Initialize microservice databases
CREATE DATABASE vishop_tenant;
CREATE DATABASE vishop_identity;
CREATE DATABASE vishop_audit;

-- Grant privileges to default user
GRANT ALL PRIVILEGES ON DATABASE vishop_tenant TO vishop;
GRANT ALL PRIVILEGES ON DATABASE vishop_identity TO vishop;
GRANT ALL PRIVILEGES ON DATABASE vishop_audit TO vishop;

-- Connect to each and ensure UUID extensions
\c vishop_tenant;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

\c vishop_identity;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

\c vishop_audit;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
