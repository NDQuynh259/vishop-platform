# SKU combos

A combo is a `merchant_skus` row with `isCombo = true`. It has no physical inventory row. Its
component list is stored in `combo_components`; every combo has at least two component SKU types,
and every component is an active, non-combo SKU in the same tenant.

## Endpoints

| Method | Path                                           | Purpose                                              |
| ------ | ---------------------------------------------- | ---------------------------------------------------- |
| `PUT`  | `/api/v1/merchant-skus/:id/combo-components`   | Replace the complete component list of a combo SKU   |
| `GET`  | `/api/v1/merchant-skus/:id/combo-availability` | Read derived availability and current component cost |

```json
{
  "components": [
    { "componentSkuId": "019dd1a5-0000-7000-8000-000000000003", "quantity": 2 },
    { "componentSkuId": "019dd1a5-0000-7000-8000-000000000004", "quantity": 1 }
  ]
}
```

For `2 × A + 1 × B`, with A available `10` and B available `3`, combo availability is
`MIN(floor(10 / 2), floor(3 / 1)) = 3`.

`costAtDispatch` is calculated on every read as `SUM(component.avgCost × quantity)`. It is never
persisted on the combo, so later receipts and weighted-average-cost changes of a component are
reflected immediately.

Database triggers reject physical inventory for a combo and reject component rows whose combo or
component is not an active SKU of the proper type. A component SKU cannot be soft-deleted while a
combo references it; the API returns `409 sku_delete_blocked`.
