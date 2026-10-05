# SKU category tree

SKU categories are managed inside `modules-products`. The existing `sku_categories` table stores an
unbounded parent/child tree. Each category has an immutable `code`, unique within its tenant, and
`merchant_skus.categoryId` keeps the stable SKU-to-category link.
Moving or renaming a category changes only its own tree fields; it never rewrites assigned SKU links.

## Endpoints

| Method | Path                                 | Purpose                                                                        |
| ------ | ------------------------------------ | ------------------------------------------------------------------------------ |
| `GET`  | `/api/v1/sku-categories/tree`        | Complete tenant category tree with `children`, `isLeaf`, and direct SKU counts |
| `POST` | `/api/v1/sku-categories`             | Create a root category or a child category with an immutable unique code       |
| `PUT`  | `/api/v1/sku-categories/:id`         | Rename and/or move a category; use `parentId: null` to move to root            |
| `GET`  | `/api/v1/sku-categories/:id/skus`    | Cursor-paginated active SKUs in the category and every descendant category     |
| `PUT`  | `/api/v1/merchant-skus/:id/category` | Assign a SKU to a leaf category or clear its category with `categoryId: null`  |

## Invariants

1. A parent category must belong to the same tenant.
2. A category cannot be moved under itself or a descendant.
3. A category with directly assigned active SKUs cannot receive children. This preserves the rule that
   SKUs are assigned only to leaf categories.
4. A SKU cannot be assigned to a category that has children.
5. Category `code` is uppercase letters/numbers separated by `-` or `_`, and is unique within a tenant.

Category tree writes take a tenant-scoped PostgreSQL advisory transaction lock. This serializes
concurrent move/create/assignment requests that could otherwise race the leaf invariant.

## Filtering and reporting by category branch

`GET /sku-categories/:id/skus` uses a recursive CTE over `sku_categories` and returns active SKUs
whose `categoryId` belongs to the selected node or any descendant. It accepts cursor pagination
with `limit` and `cursor`, plus optional `isCombo`, `status`, and a whitelist sort:
`createdAt:desc` (default), `name:asc|desc`, `skuCode:asc|desc`, or `available:asc|desc`.

The response uses `ListResponseDto`. `available` is summed from inventory for physical SKUs and
derived as `MIN(floor(component available / component quantity))` for combos. Combo rows also expose
the dynamically calculated `costAtDispatch`; physical SKU rows expose `avgCost` and a null
`costAtDispatch`. Reporting and SKU list filters must reuse the same subtree query instead of
reimplementing tree traversal.

## Bulk update

`PUT /merchant-skus/bulk` accepts `categoryId` only when the category is a leaf. An invalid or
branch category returns an item-level failure for every selected SKU with `category_not_found` or
`category_not_leaf`, and no SKU is updated for that invalid shared category change.
