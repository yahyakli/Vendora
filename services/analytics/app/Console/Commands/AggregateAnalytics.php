<?php

namespace App\Console\Commands;

use Illuminate\Console\Command;
use Illuminate\Support\Facades\DB;

class AggregateAnalytics extends Command
{
    protected $signature = 'analytics:aggregate {--day= : Aggregate a specific YYYY-MM-DD day}';

    protected $description = 'Aggregate daily analytics into reporting tables';

    public function handle(): int
    {
        $day = $this->option('day') ?: now()->subDay()->toDateString();
        $this->aggregateVendorStats($day);
        $this->aggregateProductViews($day);
        $this->info("Analytics aggregation completed for {$day}.");

        return self::SUCCESS;
    }

    private function aggregateVendorStats(string $day): void
    {
        $views = DB::table('product_views')
            ->select('vendor_id', DB::raw('SUM(view_count) as product_views'))
            ->where('day', $day)
            ->whereNotNull('vendor_id')
            ->groupBy('vendor_id')
            ->get()
            ->keyBy('vendor_id');

        foreach ($views as $vendorId => $view) {
            DB::table('vendor_stats')->updateOrInsert(
                ['vendor_id' => $vendorId, 'day' => $day],
                ['product_views' => $view->product_views, 'updated_at' => now(), 'created_at' => now()]
            );
        }
    }

    private function aggregateProductViews(string $day): void
    {
        DB::table('product_views')
            ->where('day', $day)
            ->orderBy('product_id')
            ->chunkById(500, function ($rows): void {
                foreach ($rows as $row) {
                    DB::table('product_views')->where('id', $row->id)->update(['updated_at' => now()]);
                }
            });
    }
}
