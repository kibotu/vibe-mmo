FROM composer:2.8 AS composer

FROM php:8.5.9-fpm-bookworm

RUN apt-get update \
    && apt-get install -y --no-install-recommends git unzip supervisor \
    && docker-php-ext-install -j"$(nproc)" pdo_mysql \
    && rm -rf /var/lib/apt/lists/*

# Keep local parity with the supplied FPM runtime: these extensions are not loaded there.
RUN php -r 'if (PHP_VERSION !== "8.5.9" || extension_loaded("pcntl") || extension_loaded("sockets")) { fwrite(STDERR, "Unexpected PHP runtime contract\n"); exit(1); }'

COPY --from=composer /usr/bin/composer /usr/bin/composer

WORKDIR /var/www/html/backend

COPY composer.json composer.lock ./
RUN composer install \
    --no-dev \
    --no-interaction \
    --no-progress \
    --prefer-dist \
    --classmap-authoritative

COPY . .
# The dependency install happens before application sources exist; authoritative classmaps must be rebuilt afterwards.
RUN composer dump-autoload --no-dev --classmap-authoritative
COPY docker/php/php.ini /usr/local/etc/php/conf.d/mmo.ini
COPY docker/supervisor/supervisord.conf /etc/supervisor/conf.d/mmo.conf
COPY docker/entrypoint.sh /usr/local/bin/mmo-entrypoint.sh
RUN chmod 0755 /usr/local/bin/mmo-entrypoint.sh

RUN mkdir -p runtime \
    && chown -R www-data:www-data runtime

EXPOSE 9000 8080

CMD ["/usr/local/bin/mmo-entrypoint.sh"]
