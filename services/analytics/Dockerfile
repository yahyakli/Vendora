FROM php:8.3-cli-alpine

RUN apk add --no-cache postgresql-dev libzip-dev oniguruma-dev linux-headers zip unzip git \
    && docker-php-ext-install pdo_mysql pdo_pgsql mbstring sockets zip

COPY --from=composer:latest /usr/bin/composer /usr/bin/composer

WORKDIR /var/www
COPY . .

RUN composer install --no-dev --no-interaction --prefer-dist --optimize-autoloader

RUN chown -R www-data:www-data storage bootstrap/cache

EXPOSE 8088
CMD ["php", "artisan", "serve", "--host=0.0.0.0", "--port=8088"]
