<?php

use Illuminate\Http\Request;
use Illuminate\Support\Facades\Route;

/*
|--------------------------------------------------------------------------
| API Routes
|--------------------------------------------------------------------------
|
| Here is where you can register API routes for your application. These
| routes are loaded by the RouteServiceProvider and all of them will
| be assigned to the "api" middleware group. Make something great!
|
*/

Route::prefix('analytics')->group(function () {
    Route::get('/revenue', [\App\Http\Controllers\AnalyticsController::class, 'revenue']);
    Route::get('/vendors/top', [\App\Http\Controllers\AnalyticsController::class, 'topVendors']);
    Route::get('/products/trending', [\App\Http\Controllers\AnalyticsController::class, 'trendingProducts']);
    Route::get('/categories/trending', [\App\Http\Controllers\AnalyticsController::class, 'trendingCategories']);
    Route::get('/fraud/signals', [\App\Http\Controllers\AnalyticsController::class, 'fraudSignals']);
    Route::get('/platform/summary', [\App\Http\Controllers\AnalyticsController::class, 'platformSummary']);
});
