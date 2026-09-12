<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('vendor_stats', function (Blueprint $table) {
            $table->id();
            $table->unsignedBigInteger('vendor_id');
            $table->date('day');
            $table->decimal('revenue', 14, 2)->default(0);
            $table->unsignedInteger('order_count')->default(0);
            $table->unsignedInteger('product_views')->default(0);
            $table->decimal('average_rating', 4, 2)->nullable();
            $table->timestamps();
            $table->unique(['vendor_id', 'day']);
            $table->index('vendor_id');
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('vendor_stats');
    }
};
