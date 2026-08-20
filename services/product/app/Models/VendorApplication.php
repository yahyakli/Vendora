<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;

class VendorApplication extends Model
{
    use HasFactory;

    protected $fillable = [
        'user_id',
        'store_name',
        'description',
        'business_registration_number',
        'tax_id',
        'status',
        'rejection_reason',
        'processed_by',
        'processed_at',
    ];

    protected $casts = [
        'processed_at' => 'datetime',
    ];

    public function vendor()
    {
        return $this->hasOne(Vendor::class, 'user_id', 'user_id');
    }
}
