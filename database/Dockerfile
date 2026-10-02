FROM mysql:8.4
COPY init/001-import.sql.sh /docker-entrypoint-initdb.d/001-import.sh
RUN chmod 0555 /docker-entrypoint-initdb.d/001-import.sh
