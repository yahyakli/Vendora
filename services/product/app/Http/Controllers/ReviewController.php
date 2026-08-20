<?php

namespace App\Http\Controllers;

use App\Models\Product;
use App\Models\Review;
use App\Models\Vendor;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Validator;

class ReviewController extends Controller
{
    /**
     * Display reviews for a specific product.
     */
    public function index(Request $request, $productId)
    {
        $product = Product::find($productId);
        if (!$product) {
            return response()->json(['message' => 'Product not found'], 404);
        }

        $query = Review::where('product_id', $productId)
            ->where('is_approved', true);

        // Filter by exact star rating (1-5)
        if ($request->has('rating') && is_numeric($request->rating)) {
            $query->where('rating', (int)$request->rating);
        }

        // Sorting
        $sortBy = $request->input('sort_by', 'newest');
        if ($sortBy === 'highest_rating') {
            $query->orderBy('rating', 'desc')->orderBy('created_at', 'desc');
        } elseif ($sortBy === 'lowest_rating') {
            $query->orderBy('rating', 'asc')->orderBy('created_at', 'desc');
        } elseif ($sortBy === 'most_helpful') {
            $query->orderBy('likes_count', 'desc')->orderBy('created_at', 'desc');
        } else {
            $query->orderBy('created_at', 'desc');
        }

        $reviews = $query->paginate($request->input('per_page', 10));

        // Rating distribution summary
        $distribution = [
            5 => Review::where('product_id', $productId)->where('is_approved', true)->where('rating', 5)->count(),
            4 => Review::where('product_id', $productId)->where('is_approved', true)->where('rating', 4)->count(),
            3 => Review::where('product_id', $productId)->where('is_approved', true)->where('rating', 3)->count(),
            2 => Review::where('product_id', $productId)->where('is_approved', true)->where('rating', 2)->count(),
            1 => Review::where('product_id', $productId)->where('is_approved', true)->where('rating', 1)->count(),
        ];

        return response()->json([
            'summary' => [
                'avg_rating' => (float)$product->avg_rating,
                'total_reviews' => (int)$product->reviews_count,
                'distribution' => $distribution,
            ],
            'reviews' => $reviews,
        ]);
    }

    /**
     * Store a newly created review for a product (Authenticated Buyer).
     */
    public function store(Request $request, $productId)
    {
        $userId = $request->attributes->get('user_id');
        if (!$userId) {
            return response()->json(['message' => 'Unauthorized: User ID not found'], 401);
        }

        $product = Product::find($productId);
        if (!$product) {
            return response()->json(['message' => 'Product not found'], 404);
        }

        $validator = Validator::make($request->all(), [
            'rating' => 'required|integer|min:1|max:5',
            'title' => 'nullable|string|max:255',
            'comment' => 'nullable|string|max:3000',
            'order_id' => 'nullable|integer',
        ]);

        if ($validator->fails()) {
            return response()->json(['errors' => $validator->errors()], 422);
        }

        // Check if user has already reviewed this product
        $existingReview = Review::where('product_id', $productId)
            ->where('user_id', $userId)
            ->first();

        if ($existingReview) {
            return response()->json([
                'message' => 'You have already reviewed this product. You can update your existing review.',
                'review_id' => $existingReview->id
            ], 409);
        }

        $isVerifiedPurchase = $request->has('order_id') && !empty($request->order_id);

        $review = Review::create([
            'product_id' => $productId,
            'user_id' => $userId,
            'order_id' => $request->order_id,
            'rating' => $request->rating,
            'title' => $request->title,
            'comment' => $request->comment,
            'is_verified_purchase' => $isVerifiedPurchase,
            'is_approved' => true,
        ]);

        // Recalculate ratings
        $this->recalculateRatings($product);

        return response()->json([
            'message' => 'Review submitted successfully',
            'review' => $review
        ], 201);
    }

    /**
     * Update an existing review (Owner or Admin).
     */
    public function update(Request $request, $id)
    {
        $userId = $request->attributes->get('user_id');
        $userRole = strtoupper((string)$request->attributes->get('user_role', ''));
        $userRoles = array_map('strtoupper', (array)$request->attributes->get('user_roles', []));
        $isAdmin = in_array('ADMIN', $userRoles, true) || in_array('ROLE_ADMIN', $userRoles, true) || $userRole === 'ADMIN';

        $review = Review::find($id);
        if (!$review) {
            return response()->json(['message' => 'Review not found'], 404);
        }

        if (!$isAdmin && (string)$review->user_id !== (string)$userId) {
            return response()->json(['message' => 'Forbidden: You can only edit your own reviews'], 403);
        }

        $validator = Validator::make($request->all(), [
            'rating' => 'sometimes|required|integer|min:1|max:5',
            'title' => 'nullable|string|max:255',
            'comment' => 'nullable|string|max:3000',
        ]);

        if ($validator->fails()) {
            return response()->json(['errors' => $validator->errors()], 422);
        }

        $review->update($request->only(['rating', 'title', 'comment']));

        $product = Product::find($review->product_id);
        if ($product) {
            $this->recalculateRatings($product);
        }

        return response()->json([
            'message' => 'Review updated successfully',
            'review' => $review
        ]);
    }

    /**
     * Delete a review (Owner or Admin).
     */
    public function destroy(Request $request, $id)
    {
        $userId = $request->attributes->get('user_id');
        $userRole = strtoupper((string)$request->attributes->get('user_role', ''));
        $userRoles = array_map('strtoupper', (array)$request->attributes->get('user_roles', []));
        $isAdmin = in_array('ADMIN', $userRoles, true) || in_array('ROLE_ADMIN', $userRoles, true) || $userRole === 'ADMIN';

        $review = Review::find($id);
        if (!$review) {
            return response()->json(['message' => 'Review not found'], 404);
        }

        if (!$isAdmin && (string)$review->user_id !== (string)$userId) {
            return response()->json(['message' => 'Forbidden: You can only delete your own reviews'], 403);
        }

        $productId = $review->product_id;
        $review->delete();

        $product = Product::find($productId);
        if ($product) {
            $this->recalculateRatings($product);
        }

        return response()->json(['message' => 'Review deleted successfully']);
    }

    /**
     * Helper to recalculate aggregate ratings for Product and Vendor.
     */
    private function recalculateRatings(Product $product): void
    {
        $approvedReviews = Review::where('product_id', $product->id)->where('is_approved', true);
        $count = $approvedReviews->count();
        $avg = $count > 0 ? (float)$approvedReviews->avg('rating') : 0.00;

        $product->update([
            'reviews_count' => $count,
            'avg_rating' => round($avg, 2),
        ]);

        // Recalculate Vendor average rating
        if ($product->vendor_id) {
            $vendor = Vendor::find($product->vendor_id);
            if ($vendor) {
                $vendorProductIds = Product::where('vendor_id', $vendor->id)->pluck('id');
                $vendorReviews = Review::whereIn('product_id', $vendorProductIds)->where('is_approved', true);
                $vendorCount = $vendorReviews->count();
                $vendorAvg = $vendorCount > 0 ? (float)$vendorReviews->avg('rating') : 0.00;

                $vendor->update([
                    'reviews_count' => $vendorCount,
                    'rating' => round($vendorAvg, 2),
                ]);
            }
        }
    }
}
