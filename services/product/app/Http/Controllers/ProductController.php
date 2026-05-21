<?php

namespace App\Http\Controllers;

use App\Models\Product;
use App\Models\ProductImage;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Validator;

class ProductController extends Controller
{
    /**
     * Display a listing of the products.
     */
    public function index(Request $request)
    {
        $query = Product::with(['category', 'vendor']);

        // Basic Filtering
        if ($request->has('category_id')) {
            $query->where('category_id', $request->category_id);
        }

        if ($request->has('type')) {
            $query->where('type', $request->type);
        }

        if ($request->has('status')) {
            $query->where('status', $request->status);
        }

        $products = $query->paginate($request->input('per_page', 15));

        return response()->json($products);
    }

    /**
     * Store a newly created product in storage.
     */
    public function store(Request $request)
    {
        $validator = Validator::make($request->all(), [
            'vendor_id' => 'required|exists:vendors,id',
            'category_id' => 'required|exists:categories,id',
            'subcategory_id' => 'nullable|exists:subcategories,id',
            'name' => 'required|string|max:255',
            'description' => 'required|string',
            'short_description' => 'nullable|string|max:255',
            'price' => 'required|numeric|min:0',
            'compare_at_price' => 'nullable|numeric|min:0',
            'cost_price' => 'nullable|numeric|min:0',
            'sku' => 'required|string|unique:products,sku',
            'type' => 'required|in:physical,digital',
            'status' => 'nullable|in:draft,pending_approval,active,flagged,inactive',
            'weight' => 'nullable|numeric|min:0',
            'dimensions' => 'nullable|array',
        ]);

        if ($validator->fails()) {
            return response()->json(['errors' => $validator->errors()], 422);
        }

        $product = Product::create($request->all());

        return response()->json([
            'message' => 'Product created successfully',
            'product' => $product
        ], 201);
    }

    /**
     * Display the specified product.
     */
    public function show($id)
    {
        $product = Product::with(['category', 'vendor', 'images', 'reviews'])->find($id);

        if (!$product) {
            return response()->json(['message' => 'Product not found'], 404);
        }

        return response()->json($product);
    }

    /**
     * Update the specified product in storage.
     */
    public function update(Request $request, $id)
    {
        $product = Product::find($id);

        if (!$product) {
            return response()->json(['message' => 'Product not found'], 404);
        }

        $validator = Validator::make($request->all(), [
            'category_id' => 'sometimes|required|exists:categories,id',
            'subcategory_id' => 'nullable|exists:subcategories,id',
            'name' => 'sometimes|required|string|max:255',
            'description' => 'sometimes|required|string',
            'short_description' => 'nullable|string|max:255',
            'price' => 'sometimes|required|numeric|min:0',
            'compare_at_price' => 'nullable|numeric|min:0',
            'cost_price' => 'nullable|numeric|min:0',
            'sku' => 'sometimes|required|string|unique:products,sku,' . $id,
            'type' => 'sometimes|required|in:physical,digital',
            'status' => 'nullable|in:draft,pending_approval,active,flagged,inactive',
            'weight' => 'nullable|numeric|min:0',
            'dimensions' => 'nullable|array',
        ]);

        if ($validator->fails()) {
            return response()->json(['errors' => $validator->errors()], 422);
        }

        $product->update($request->all());

        return response()->json([
            'message' => 'Product updated successfully',
            'product' => $product
        ]);
    }

    /**
     * Remove the specified product from storage.
     */
    public function destroy($id)
    {
        $product = Product::find($id);

        if (!$product) {
            return response()->json(['message' => 'Product not found'], 404);
        }

        $product->delete();

        return response()->json(['message' => 'Product deleted successfully']);
    }

    /**
     * Upload an image for a product.
     */
    public function uploadImage(Request $request, $id)
    {
        $product = Product::find($id);

        if (!$product) {
            return response()->json(['message' => 'Product not found'], 404);
        }

        $validator = Validator::make($request->all(), [
            'image' => 'required|image|mimes:jpeg,png,jpg,gif,webp|max:5120', // 5MB max
            'is_primary' => 'nullable|boolean',
        ]);

        if ($validator->fails()) {
            return response()->json(['errors' => $validator->errors()], 422);
        }

        $file = $request->file('image');
        $token = $request->bearerToken();

        try {
            // 1. Upload to Storage Service
            $uploadUrl = config('services.storage.url') . '/storage/upload';
            
            $uploadResponse = Http::withToken($token)
                ->attach('file', file_get_contents($file->path()), $file->getClientOriginalName())
                ->post($uploadUrl, [
                    'bucket_type' => 'products',
                    'product_id' => $id,
                ]);

            if ($uploadResponse->failed()) {
                return response()->json([
                    'message' => 'Failed to upload image to storage service',
                    'error' => $uploadResponse->json('message', 'Unknown error')
                ], 502);
            }

            $fileKey = $uploadResponse->json('file_key');

            // 2. Get Signed/Public URL
            $urlResponse = Http::withToken($token)
                ->get(config('services.storage.url') . '/storage/url/' . $fileKey, [
                    'bucket_type' => 'products'
                ]);

            if ($urlResponse->failed()) {
                return response()->json(['message' => 'Failed to get image URL'], 502);
            }

            $imageUrl = $urlResponse->json('url');

            // 3. Save to database
            $productImage = ProductImage::create([
                'product_id' => $id,
                'image_url' => $imageUrl,
                'is_primary' => $request->input('is_primary', false),
            ]);

            return response()->json([
                'message' => 'Image uploaded successfully',
                'image' => $productImage
            ], 201);

        } catch (\Exception $e) {
            return response()->json([
                'message' => 'Error communicating with storage service',
                'error' => $e->getMessage()
            ], 503);
        }
    }
}
