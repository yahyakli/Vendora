<?php

namespace App\Http\Controllers;

use App\Models\Product;
use App\Models\Wishlist;
use Illuminate\Http\Request;

class WishlistController extends Controller
{
    /**
     * Display the authenticated user's wishlist.
     */
    public function index(Request $request)
    {
        $userId = $request->attributes->get('user_id');
        if (!$userId) {
            return response()->json(['message' => 'Unauthorized'], 401);
        }

        $wishlists = Wishlist::where('user_id', $userId)
            ->with(['product' => function ($query) {
                $query->with(['images', 'category', 'vendor']);
            }])
            ->orderBy('created_at', 'desc')
            ->paginate($request->input('per_page', 20));

        return response()->json($wishlists);
    }

    /**
     * Add a product to the authenticated user's wishlist.
     */
    public function store(Request $request, $productId)
    {
        $userId = $request->attributes->get('user_id');
        if (!$userId) {
            return response()->json(['message' => 'Unauthorized'], 401);
        }

        $product = Product::find($productId);
        if (!$product) {
            return response()->json(['message' => 'Product not found'], 404);
        }

        $wishlist = Wishlist::firstOrCreate(
            [
                'user_id' => $userId,
                'product_id' => $productId,
            ]
        );

        return response()->json([
            'message' => 'Product added to wishlist',
            'wishlist' => $wishlist->load(['product.images'])
        ], 201);
    }

    /**
     * Remove a product from the authenticated user's wishlist.
     */
    public function destroy(Request $request, $productId)
    {
        $userId = $request->attributes->get('user_id');
        if (!$userId) {
            return response()->json(['message' => 'Unauthorized'], 401);
        }

        $deleted = Wishlist::where('user_id', $userId)
            ->where('product_id', $productId)
            ->delete();

        if (!$deleted) {
            return response()->json(['message' => 'Product not found in wishlist'], 404);
        }

        return response()->json(['message' => 'Product removed from wishlist']);
    }
}
