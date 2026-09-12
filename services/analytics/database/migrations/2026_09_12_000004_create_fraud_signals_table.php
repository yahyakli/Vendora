<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('fraud_signals', function (Blueprint $table) {
            $table->id();
            $table->string('signal_type');
            $table->unsignedBigInteger('user_id')->nullable();
            $table->unsignedBigInteger('vendor_id')->nullable();
            $table->unsignedBigInteger('order_id')->nullable();
            $table->string('severity')->default('medium');
            $table->json('details')->nullable();
            $table->timestamp('detected_at');
            $table->timestamp('resolved_at')->nullable();
            $table->timestamps();
            $table->index(['signal_type', 'severity']);
            $table->index('detected_at');
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('fraud_signals');
    }
};
