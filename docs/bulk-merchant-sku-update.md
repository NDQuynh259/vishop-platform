# Bulk merchant SKU update

`PUT /api/v1/merchant-skus/bulk` updates up to 100 tenant-owned SKUs in one request. The request
must contain `merchantSkuIds` and at least one editable field:

```json
{
  "merchantSkuIds": ["019dd1a5-0000-7000-8000-000000000001"],
  "avgCost": 50000,
  "categoryId": "019dd1a5-0000-7000-8000-000000000010",
  "status": "DISCONTINUED",
  "lowStockThreshold": 10
}
```

All fields are optional. `categoryId` and `lowStockThreshold` accept `null` to clear their values.
`avgCost` is a non-negative integer in the tenant currency and `status` is `ACTIVE` or `DISCONTINUED`.

The endpoint always returns `200` for a valid request, including partial failures:

```json
{
  "succeeded": 1,
  "failed": 1,
  "results": [
    { "merchantSkuId": "019...001", "status": "updated" },
    { "merchantSkuId": "019...002", "status": "failed", "reason": "not_found" }
  ]
}
```

An invalid category causes every selected SKU to return `category_not_found`; a non-leaf category
returns `category_not_leaf`. In both cases no SKU is updated. Missing, foreign, or soft-deleted SKU
ids return `not_found` independently. `DISCONTINUED` is the supported lifecycle state for SKUs that
cannot be deleted under BR-03-04.
