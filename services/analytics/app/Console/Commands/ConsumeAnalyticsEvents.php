<?php

namespace App\Console\Commands;

use Carbon\CarbonImmutable;
use Illuminate\Console\Command;
use Illuminate\Support\Facades\DB;
use PhpAmqpLib\Connection\AMQPStreamConnection;
use PhpAmqpLib\Message\AMQPMessage;
use Throwable;

class ConsumeAnalyticsEvents extends Command
{
    protected $signature = 'analytics:consume-events';

    protected $description = 'Consume order and product events from RabbitMQ';

    private const EVENT_KEYS = ['order.completed', 'product.viewed'];

    public function handle(): int
    {
        $connection = null;
        $channel = null;

        try {
            [$connection, $channel] = $this->connect();
            $this->info('Analytics consumer listening for order.completed and product.viewed.');

            $channel->basic_consume(
                config('services.rabbitmq.queue'),
                '',
                false,
                false,
                false,
                false,
                function (AMQPMessage $message): void {
                    $routingKey = $message->getRoutingKey();

                    try {
                        $payload = json_decode($message->getBody(), true, 512, JSON_THROW_ON_ERROR);
                        $this->processEvent($routingKey, $payload);
                        $message->getChannel()->basic_ack($message->getDeliveryTag());
                    } catch (Throwable $exception) {
                        $this->error(sprintf('Analytics event failed: %s', $exception->getMessage()));
                        $message->getChannel()->basic_nack($message->getDeliveryTag(), false, false);
                    }
                }
            );

            while ($channel->is_consuming()) {
                $channel->wait();
            }
        } catch (Throwable $exception) {
            $this->error(sprintf('Analytics consumer stopped: %s', $exception->getMessage()));
            return self::FAILURE;
        } finally {
            $channel?->close();
            $connection?->close();
        }

        return self::SUCCESS;
    }

    private function connect(): array
    {
        $connection = new AMQPStreamConnection(
            config('services.rabbitmq.host'),
            (int) config('services.rabbitmq.port'),
            config('services.rabbitmq.username'),
            config('services.rabbitmq.password')
        );
        $channel = $connection->channel();
        $exchange = config('services.rabbitmq.exchange');
        $queue = config('services.rabbitmq.queue');

        $channel->exchange_declare($exchange, 'topic', false, true, false);
        $channel->queue_declare($queue, false, true, false, false);
        foreach (self::EVENT_KEYS as $routingKey) {
            $channel->queue_bind($queue, $exchange, $routingKey);
        }
        $channel->basic_qos(null, 10, null);

        return [$connection, $channel];
    }

    private function processEvent(?string $routingKey, array $payload): void
    {
        match ($routingKey) {
            'order.completed' => $this->recordCompletedOrder($payload),
            'product.viewed' => $this->recordProductView($payload),
            default => throw new \InvalidArgumentException("Unsupported analytics event: {$routingKey}"),
        };
    }

    private function recordCompletedOrder(array $payload): void
    {
        $day = $this->eventDay($payload);
        $currency = strtoupper((string) ($payload['currency'] ?? 'USD'));
        $orderId = $this->integerValue($payload, ['order_id', 'orderId']);
        $total = $this->decimalValue($payload, ['total', 'amount', 'order_total']);

        DB::transaction(function () use ($day, $currency, $orderId, $total, $payload): void {
            $revenue = DB::table('daily_revenue')->where('day', $day)->where('currency', $currency)->first();
            DB::table('daily_revenue')->updateOrInsert(
                ['day' => $day, 'currency' => $currency],
                [
                    'revenue' => ($revenue?->revenue ?? 0) + $total,
                    'order_count' => ($revenue?->order_count ?? 0) + 1,
                    'updated_at' => now(),
                    'created_at' => $revenue?->created_at ?? now(),
                ]
            );

            foreach ($payload['items'] ?? [] as $item) {
                $vendorId = $this->integerValue($item, ['vendor_id', 'vendorId']);
                if (!$vendorId) {
                    continue;
                }

                $itemRevenue = $this->decimalValue($item, ['total', 'subtotal', 'amount'])
                    ?: ($this->decimalValue($item, ['price']) * max(1, (int) ($item['quantity'] ?? 1)));
                $vendorStats = DB::table('vendor_stats')->where('vendor_id', $vendorId)->where('day', $day)->first();
                DB::table('vendor_stats')->updateOrInsert(
                    ['vendor_id' => $vendorId, 'day' => $day],
                    [
                        'revenue' => ($vendorStats?->revenue ?? 0) + $itemRevenue,
                        'order_count' => ($vendorStats?->order_count ?? 0) + 1,
                        'updated_at' => now(),
                        'created_at' => $vendorStats?->created_at ?? now(),
                    ]
                );
            }
        });

        $this->line(sprintf('Recorded completed order %s.', $orderId ?? 'unknown'));
    }

    private function recordProductView(array $payload): void
    {
        $productId = $this->integerValue($payload, ['product_id', 'productId']);
        if (!$productId) {
            throw new \InvalidArgumentException('product.viewed requires product_id');
        }

        $day = $this->eventDay($payload);
        $vendorId = $this->integerValue($payload, ['vendor_id', 'vendorId']);
        $view = DB::table('product_views')->where('product_id', $productId)->where('day', $day)->first();

        DB::table('product_views')->updateOrInsert(
            ['product_id' => $productId, 'day' => $day],
            [
                'vendor_id' => $vendorId ?: $view?->vendor_id,
                'view_count' => ($view?->view_count ?? 0) + 1,
                'updated_at' => now(),
                'created_at' => $view?->created_at ?? now(),
            ]
        );

        if ($vendorId) {
            $vendorStats = DB::table('vendor_stats')->where('vendor_id', $vendorId)->where('day', $day)->first();
            DB::table('vendor_stats')->updateOrInsert(
                ['vendor_id' => $vendorId, 'day' => $day],
                [
                    'product_views' => ($vendorStats?->product_views ?? 0) + 1,
                    'updated_at' => now(),
                    'created_at' => $vendorStats?->created_at ?? now(),
                ]
            );
        }
    }

    private function eventDay(array $payload): string
    {
        $timestamp = $payload['occurred_at'] ?? $payload['timestamp'] ?? null;
        return $timestamp ? CarbonImmutable::parse($timestamp)->toDateString() : now()->toDateString();
    }

    private function integerValue(array $payload, array $keys): ?int
    {
        foreach ($keys as $key) {
            if (isset($payload[$key]) && is_numeric($payload[$key])) {
                return (int) $payload[$key];
            }
        }
        return null;
    }

    private function decimalValue(array $payload, array $keys): float
    {
        foreach ($keys as $key) {
            if (isset($payload[$key]) && is_numeric($payload[$key])) {
                return (float) $payload[$key];
            }
        }
        return 0.0;
    }
}
