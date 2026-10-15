#!/bin/bash
# DB와 업로드 파일(사진·PDF)을 /opt/atopass/backups 에 백업하고 14일이 지난 백업은 지운다.
# 서버에서 직접 실행하거나 cron으로 매일 실행한다.  예) 0 4 * * * /opt/atopass/backup.sh
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p backups
STAMP=$(date +%Y%m%d-%H%M%S)

docker compose exec -T mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines atopass' \
  | gzip > "backups/db-$STAMP.sql.gz"
docker run --rm -v atopass_app-data:/data:ro -v "$PWD/backups":/out alpine \
  tar czf "/out/files-$STAMP.tgz" -C /data .

find backups -type f -mtime +14 -delete
echo "백업 완료: backups/db-$STAMP.sql.gz, backups/files-$STAMP.tgz"
