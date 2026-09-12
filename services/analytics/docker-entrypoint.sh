#!/bin/sh
set -eu

attempt=1
max_attempts=${ANALYTICS_MIGRATE_RETRIES:-30}
until php artisan migrate --force; do
	if [ "$attempt" -ge "$max_attempts" ]; then
		echo "Analytics database migrations failed after ${max_attempts} attempts."
		exit 1
	fi

	echo "Waiting for Analytics database (attempt ${attempt}/${max_attempts})..."
	attempt=$((attempt + 1))
	sleep 2
done

php artisan serve --host=0.0.0.0 --port=8088 &
http_pid=$!
php artisan analytics:consume-events &
consumer_pid=$!

trap 'kill "$http_pid" "$consumer_pid" 2>/dev/null || true' TERM INT
wait "$http_pid" "$consumer_pid"
