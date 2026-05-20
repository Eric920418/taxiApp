#!/usr/bin/env bash
# ============================================================
# 完成 GoGoCha audit 6 Cap 部署（已在 VPS 上 in-place 改完代碼）
#
# 已過 typecheck:
#   ✓ pnpm tsc --noEmit
#   ✓ admin-panel pnpm build
#
# 此腳本剩下做：
#   1. SSH VPS commit + push origin main
#   2. server pnpm build (transpile)
#   3. 跑 migration 020 + 021
#   4. pm2 restart taxiserver
#   5. git stash pop （還原 config/fareConfig.json）
#
# 需要：~/.ssh/LightsailDefaultKey-ap-northeast-5.pem
# ============================================================
set -euo pipefail

KEY="$HOME/.ssh/LightsailDefaultKey-ap-northeast-5.pem"
VPS="ubuntu@15.164.245.47"

[[ -f "$KEY" ]] || { echo "❌ SSH key 不存在: $KEY"; exit 1; }

echo "🚀 連 VPS 完成部署..."
ssh -i "$KEY" -o StrictHostKeyChecking=no "$VPS" 'bash -s' << 'OUTER_EOF'
set -e
cd /var/www/taxiServer
source ~/.nvm/nvm.sh 2>/dev/null || true

echo "=== [1] git commit + push ==="
git -c user.name="VPS Deploy" -c user.email="deploy@hualientaxi.taxi" \
  commit -m "feat(audit): GoGoCha 6 Cap 補強 — commission 預設 + Partner UI + 自動 re-queue + LIFF priority + backfill + 既有 bug

[Cap 1] partners.default_order_commission_pct + orders.commission_pct DEFAULT 5
[Cap 2] admin Drivers.tsx 加 3 個 Partner select (PRIMARY_FLEET/BRAND/RECRUITED_BY)
[Cap 3] PATCH /status DONE 後 GPS 在 dispatched_from_zone 內自動 re-queue
[Cap 4] LIFF booking.html 派單優先度 radio (0/5/10/20%) + 後端接 commissionPct
[Cap 5] scripts/backfill-billing.ts (dry-run / --apply)
[Cap 6] Migration 021 補 drivers.fcm_token + customer_notifications.phone_or_line_id

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"

git push origin main

echo
echo "=== [2] server pnpm build ==="
pnpm build 2>&1 | tail -5

echo
echo "=== [3] migration 020 (commission defaults) ==="
npx ts-node src/db/migrate.ts commission-defaults

echo
echo "=== [4] migration 021 (fix missing columns) ==="
npx ts-node src/db/migrate.ts fix-missing-cols

echo
echo "=== [5] pm2 restart ==="
pm2 restart taxiserver
sleep 2
pm2 list 2>&1 | grep taxiserver | head -2

echo
echo "=== [6] 還原 fareConfig stash ==="
if git stash list | grep -q "before-cap-fixes"; then
  git stash pop "$(git stash list | grep before-cap-fixes | head -1 | sed 's/:.*//')"
  echo "✓ stash popped"
else
  echo "(no stash to pop)"
fi

echo
echo "=== [7] 30 秒 log 抽檢 ==="
pm2 logs taxiserver --lines 20 --nostream 2>&1 | tail -25
OUTER_EOF

echo
echo "🎉 部署完成"
echo
echo "可選：補歷史 billing snapshot"
echo "  ssh -i $KEY $VPS 'cd /var/www/taxiServer && npx ts-node scripts/backfill-billing.ts'"
echo "  → 確認 dry-run 列表 OK 後加 --apply"
