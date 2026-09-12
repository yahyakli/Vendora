CREATE DATABASE IF NOT EXISTS vendora_orders;
CREATE DATABASE IF NOT EXISTS vendora_analytics;
GRANT ALL PRIVILEGES ON vendora_orders.* TO 'vendora'@'%';
GRANT ALL PRIVILEGES ON vendora_analytics.* TO 'vendora'@'%';
FLUSH PRIVILEGES;
