#!/bin/sh
set -eu

attempt=0
until php /var/www/html/backend/bin/migrate.php; do
  attempt=$((attempt + 1))
  if [ "$attempt" -ge 10 ]; then
    echo "Database migrations failed after ${attempt} attempts" >&2
    exit 1
  fi
  sleep 2
done

exec /usr/bin/supervisord -c /etc/supervisor/conf.d/mmo.conf
