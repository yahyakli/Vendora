<?php

namespace App\Http\Controllers;

use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

class AnalyticsController extends Controller
{
    public function revenue(Request $request): JsonResponse
    {
        $days = min(max((int) $request->integer('days', 30), 1), 365);
        $from = now()->subDays($days - 1)->toDateString();

        $rows = DB::table('daily_revenue')
            ->where('day', '>=', $from)
            ->orderBy('day')
            ->get();

        return response()->json([
            'range_days' => $days,
            'total_revenue' => $rows->sum(fn ($row) => (float) $row->revenue),
            'total_orders' => $rows->sum('order_count'),
            'daily' => $rows,
        ]);
    }

    public function topVendors(Request $request): JsonResponse
    {
        $limit = min(max((int) $request->integer('limit', 10), 1), 100);
        $days = min(max((int) $request->integer('days', 30), 1), 365);
        $from = now()->subDays($days - 1)->toDateString();

        return response()->json([
            'items' => DB::table('vendor_stats')
                ->select('vendor_id', DB::raw('SUM(revenue) as revenue'), DB::raw('SUM(order_count) as order_count'), DB::raw('SUM(product_views) as product_views'))
                ->where('day', '>=', $from)
                ->groupBy('vendor_id')
                ->orderByDesc('revenue')
                ->limit($limit)
                ->get(),
        ]);
    }

    public function trendingProducts(Request $request): JsonResponse
    {
        $limit = min(max((int) $request->integer('limit', 10), 1), 100);
        $days = min(max((int) $request->integer('days', 7), 1), 365);
        $from = now()->subDays($days - 1)->toDateString();

        return response()->json([
            'items' => DB::table('product_views')
                ->select('product_id', 'vendor_id', DB::raw('SUM(view_count) as views'))
                ->where('day', '>=', $from)
                ->groupBy('product_id', 'vendor_id')
                ->orderByDesc('views')
                ->limit($limit)
                ->get(),
        ]);
    }

    public function trendingCategories(Request $request): JsonResponse
    {
        $limit = min(max((int) $request->integer('limit', 10), 1), 100);
        $days = min(max((int) $request->integer('days', 7), 1), 365);

        return response()->json([
            'items' => DB::table('product_views')
                ->select('category_id', DB::raw('SUM(view_count) as views'))
                ->whereNotNull('category_id')
                ->where('day', '>=', now()->subDays($days - 1)->toDateString())
                ->groupBy('category_id')
                ->orderByDesc('views')
                ->limit($limit)
                ->get(),
        ]);
    }

    public function fraudSignals(Request $request): JsonResponse
    {
        $limit = min(max((int) $request->integer('limit', 50), 1), 200);
        $query = DB::table('fraud_signals')->orderByDesc('detected_at');

        if ($request->filled('severity')) {
            $query->where('severity', $request->string('severity')->toString());
        }
        if ($request->boolean('resolved_only')) {
            $query->whereNotNull('resolved_at');
        } elseif ($request->boolean('open_only', true)) {
            $query->whereNull('resolved_at');
        }

        return response()->json(['items' => $query->limit($limit)->get()]);
    }

    public function platformSummary(): JsonResponse
    {
        $summary = DB::table('daily_revenue')
            ->selectRaw('COALESCE(SUM(revenue), 0) as gmv, COALESCE(SUM(order_count), 0) as orders')
            ->where('day', '>=', now()->subDays(29)->toDateString())
            ->first();

        return response()->json([
            'gmv_30d' => (float) $summary->gmv,
            'orders_30d' => (int) $summary->orders,
            'active_vendors_30d' => DB::table('vendor_stats')->where('day', '>=', now()->subDays(29)->toDateString())->distinct()->count('vendor_id'),
            'product_views_30d' => (int) DB::table('product_views')->where('day', '>=', now()->subDays(29)->toDateString())->sum('view_count'),
            'open_fraud_signals' => DB::table('fraud_signals')->whereNull('resolved_at')->count(),
        ]);
    }
}
