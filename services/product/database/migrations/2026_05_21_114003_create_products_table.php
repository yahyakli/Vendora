<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Run the migrations.
     */
    public function up(): void
    {
        Schema::create('products', function (Blueprint $table) {
            $table->id();
            $table->foreignId('vendor_id')->constrained('vendors')->onDelete('cascade');
            $table->foreignId('category_id')->constrained('categories')->onDelete('restrict');
            $table->foreignId('subcategory_id')->nullable()->constrained('subcategories')->onDelete('set null');
            $table->string('name');
            $table->string('slug')->unique();
            $table->longText('description'); // longText supports HTML/JSON rich text formatting
            $table->string('short_description')->nullable();
            $table->unsignedDecimal('price', 10, 2);
            $table->unsignedDecimal('compare_at_price', 10, 2)->nullable();
            $table->unsignedDecimal('cost_price', 10, 2)->nullable();
            $table->string('sku')->unique();
            $table->enum('type', ['physical', 'digital'])->default('physical');
            $table->enum('status', ['draft', 'pending_approval', 'active', 'flagged', 'inactive'])->default('active');
            $table->boolean('is_featured')->default(false);
            $table->decimal('weight', 8, 2)->nullable(); // in kg
            $table->json('dimensions')->nullable(); // { width, height, length }
            $table->decimal('avg_rating', 3, 2)->default(0.00);
            $table->unsignedInteger('reviews_count')->default(0);
            $table->unsignedInteger('sales_count')->default(0);
            $table->softDeletes();
            $table->timestamps();

            $table->index('slug');
            $table->index('sku');
        });
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::dropIfExists('products');
    }
};
