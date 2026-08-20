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

    protected $casts = [
        'rating' => 'float',
        'reviews_count' => 'integer',
        'commission_rate' => 'float',
        'verified_at' => 'datetime',
    ];

    public function products()
    {
        return $this->hasMany(Product::class);
    }

    public function applications()
    {
        return $this->hasMany(VendorApplication::class, 'user_id', 'user_id');
    }

    public function scopeActive($query)
    {
        return $query->where('status', 'active');
    }
}
