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
        Schema::create('digital_files', function (Blueprint $table) {
            $table->id();
            $table->foreignId('product_id')->constrained('products')->onDelete('cascade');
            $table->string('file_path'); // S3 key/path
            $table->string('file_name'); // original filename
            $table->unsignedBigInteger('file_size');
            $table->string('mime_type')->nullable();
            $table->string('extension')->nullable();
            $table->string('version')->default('1.0.0');
            $table->integer('download_limit')->nullable();
            $table->integer('expiry_days')->nullable();
            $table->timestamps();
        });
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::dropIfExists('digital_files');
    }
};
