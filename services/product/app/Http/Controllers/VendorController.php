<?php

namespace App\Http\Controllers;

use App\Models\Vendor;
use App\Models\VendorApplication;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Validator;
use Illuminate\Support\Str;

class VendorController extends Controller
{
    /**
     * Display a listing of vendors.
     */
    public function index(Request $request)
    {
        $query = Vendor::query();

        // Status filter: default to 'active' for public, or filterable
        if ($request->has('status')) {
            $query->where('status', $request->status);
        } else {
            $query->where('status', 'active');
        }

        // Search by store name or description
        if ($request->has('search') && !empty($request->search)) {
            $search = '%' . $request->search . '%';
            $query->where(function ($q) use ($search) {
                $q->where('store_name', 'ILIKE', $search)
                  ->orWhere('description', 'ILIKE', $search);
            });
        }

        // Sorting
        $sortBy = $request->input('sort_by', 'rating');
        if ($sortBy === 'rating') {
            $query->orderBy('rating', 'desc');
        } elseif ($sortBy === 'newest') {
            $query->orderBy('created_at', 'desc');
        } elseif ($sortBy === 'name') {
            $query->orderBy('store_name', 'asc');
        } else {
            $query->orderBy('id', 'desc');
        }

        $vendors = $query->paginate($request->input('per_page', 15));

        return response()->json($vendors);
    }

    /**
     * Display the specified vendor by ID or slug.
     */
    public function show($id)
    {
        $query = Vendor::with(['products' => function ($q) {
            $q->where('status', 'active')->with('images')->take(12);
        }]);

        if (is_numeric($id)) {
            $vendor = $query->where('id', $id)->first();
        } else {
            $vendor = $query->where('slug', $id)->first();
        }

        if (!$vendor) {
            return response()->json(['message' => 'Vendor not found'], 404);
        }

        return response()->json($vendor);
    }

    /**
     * Submit an application to become a vendor.
     */
    public function apply(Request $request)
    {
        $userId = $request->attributes->get('user_id');
        if (!$userId) {
            return response()->json(['message' => 'Unauthorized: User ID not found'], 401);
        }

        $validator = Validator::make($request->all(), [
            'store_name' => 'required|string|max:255',
            'description' => 'required|string',
            'business_registration_number' => 'nullable|string|max:100',
            'tax_id' => 'nullable|string|max:100',
            'payout_email' => 'nullable|email|max:255',
        ]);

        if ($validator->fails()) {
            return response()->json(['errors' => $validator->errors()], 422);
        }

        // Check if user is already an active vendor
        $existingVendor = Vendor::where('user_id', $userId)->where('status', 'active')->first();
        if ($existingVendor) {
            return response()->json([
                'message' => 'You already have an active vendor store',
                'vendor' => $existingVendor
            ], 400);
        }

        // Check if user already has a pending application
        $pendingApplication = VendorApplication::where('user_id', $userId)
            ->where('status', 'pending')
            ->first();
        if ($pendingApplication) {
            return response()->json([
                'message' => 'You already have a pending vendor application under review',
                'application' => $pendingApplication
            ], 400);
        }

        // Check if store_name is already taken in active vendors
        if (Vendor::where('store_name', $request->store_name)->exists()) {
            return response()->json([
                'message' => 'A vendor with this store name already exists'
            ], 422);
        }

        $application = VendorApplication::create([
            'user_id' => $userId,
            'store_name' => $request->store_name,
            'description' => $request->description,
            'business_registration_number' => $request->business_registration_number,
            'tax_id' => $request->tax_id,
            'status' => 'pending',
        ]);

        return response()->json([
            'message' => 'Vendor application submitted successfully and is pending approval',
            'application' => $application
        ], 201);
    }

    /**
     * Approve a vendor application or activate an existing vendor (Admin only).
     */
    public function approve(Request $request, $id)
    {
        $adminId = $request->attributes->get('user_id');

        // Check if $id matches a pending VendorApplication
        $application = VendorApplication::find($id);
        if ($application) {
            if ($application->status === 'approved') {
                $existingVendor = Vendor::where('user_id', $application->user_id)->first();
                return response()->json([
                    'message' => 'Vendor application was already approved',
                    'vendor' => $existingVendor,
                    'application' => $application
                ]);
            }

            $application->update([
                'status' => 'approved',
                'processed_by' => $adminId,
                'processed_at' => now(),
            ]);

            // Generate unique slug
            $slug = Str::slug($application->store_name);
            $baseSlug = $slug;
            $counter = 1;
            while (Vendor::where('slug', $slug)->where('user_id', '!=', $application->user_id)->exists()) {
                $slug = $baseSlug . '-' . $counter++;
            }

            $vendor = Vendor::updateOrCreate(
                ['user_id' => $application->user_id],
                [
                    'store_name' => $application->store_name,
                    'slug' => $slug,
                    'description' => $application->description,
                    'status' => 'active',
                    'commission_rate' => $request->input('commission_rate', 10.00),
                    'verified_at' => now(),
                ]
            );

            return response()->json([
                'message' => 'Vendor application approved and store activated successfully',
                'vendor' => $vendor,
                'application' => $application
            ]);
        }

        // Check if $id is an existing Vendor ID
        $vendor = Vendor::find($id);
        if ($vendor) {
            $vendor->update([
                'status' => 'active',
                'verified_at' => now(),
            ]);

            return response()->json([
                'message' => 'Vendor activated successfully',
                'vendor' => $vendor
            ]);
        }

        return response()->json(['message' => 'Vendor or application not found'], 404);
    }

    /**
     * Suspend a vendor (Admin only).
     */
    public function suspend(Request $request, $id)
    {
        $vendor = Vendor::find($id);

        if (!$vendor) {
            return response()->json(['message' => 'Vendor not found'], 404);
        }

        $vendor->update([
            'status' => 'suspended'
        ]);

        return response()->json([
            'message' => 'Vendor suspended successfully',
            'vendor' => $vendor
        ]);
    }

    /**
     * Get current authenticated user's vendor profile or application status.
     */
    public function me(Request $request)
    {
        $userId = $request->attributes->get('user_id');
        if (!$userId) {
            return response()->json(['message' => 'Unauthorized'], 401);
        }

        $vendor = Vendor::where('user_id', $userId)->with('products')->first();
        $latestApplication = VendorApplication::where('user_id', $userId)->latest()->first();

        return response()->json([
            'vendor' => $vendor,
            'application' => $latestApplication
        ]);
    }

    /**
     * List vendor applications (Admin only).
     */
    public function applications(Request $request)
    {
        $query = VendorApplication::query();

        if ($request->has('status')) {
            $query->where('status', $request->status);
        } else {
            $query->where('status', 'pending');
        }

        $applications = $query->orderBy('created_at', 'desc')->paginate($request->input('per_page', 15));

        return response()->json($applications);
    }
}
