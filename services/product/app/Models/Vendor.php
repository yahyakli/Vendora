<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\SoftDeletes;

class Vendor extends Model
{
    use HasFactory, SoftDeletes;

    protected $fillable = [
        'user_id',
        'store_name',
        'slug',
        'description',
        'logo_url',
        'banner_url',
        'status',
        'rating',
        'reviews_count',
        'commission_rate',
        'payout_email',
        'verified_at',
    ];

    public function products()
    {
        return $this->hasMany(Product::class);
    }
}
