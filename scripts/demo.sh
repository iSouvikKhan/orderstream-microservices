#!/usr/bin/env bash
# End-to-end walkthrough against a running stack (docker compose up).
set -euo pipefail
GW=${GATEWAY:-http://localhost:8080}

echo "==> Issuing a demo JWT for 'souvik'"
TOKEN=$(curl -s -X POST "$GW/auth/token" -H 'Content-Type: application/json' \
  -d '{"username":"souvik"}' | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')

echo "==> Stock before"
curl -s "$GW/api/inventory/products/SKU-KEYBOARD"; echo

echo "==> Happy path: place an order that will be confirmed"
OK=$(curl -s -X POST "$GW/api/orders" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"items":[{"productId":"SKU-KEYBOARD","quantity":1,"unitPrice":79.99},{"productId":"SKU-MOUSE","quantity":2,"unitPrice":24.50}]}')
echo "$OK"
OK_ID=$(echo "$OK" | sed -E 's/.*"id":"([^"]+)".*/\1/')

echo "==> Compensation path: total above the payment limit, so payment fails and stock is released"
BAD=$(curl -s -X POST "$GW/api/orders" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"items":[{"productId":"SKU-LAPTOP","quantity":3,"unitPrice":2499.00}]}')
echo "$BAD"
BAD_ID=$(echo "$BAD" | sed -E 's/.*"id":"([^"]+)".*/\1/')

sleep 4
echo "==> Final states"
curl -s "$GW/api/orders/$OK_ID" -H "Authorization: Bearer $TOKEN"; echo
curl -s "$GW/api/orders/$BAD_ID" -H "Authorization: Bearer $TOKEN"; echo
echo "==> Laptop stock (should be unchanged after compensation)"
curl -s "$GW/api/inventory/products/SKU-LAPTOP"; echo
echo "==> Notifications"
curl -s "$GW/api/notifications" -H "Authorization: Bearer $TOKEN"; echo
