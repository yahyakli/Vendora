<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('daily_revenue', function (Blueprint $table) {
            $table->id();
            $table->date('day');
            $table->decimal('revenue', 14, 2)->default(0);
            $table->unsignedInteger('order_count')->default(0);
            $table->string('currency', 3)->default('USD');
            $table->timestamps();
            $table->unique(['day', 'currency']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('daily_revenue');
    }
};
