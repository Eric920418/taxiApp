#!/usr/bin/env bash
# ============================================================
# 一鍵補完 audit 6 個 Cap 漏掉的部分 + 部署
#
# 已寫進檔案（在 macOS Desktop access 鎖之前）：
#   - migrations 020/021 兩個 SQL
#   - orders.ts (Cap 1+3)
#   - Drivers.tsx (Cap 2)
#   - backfill-billing.ts (Cap 5)
#   - booking.html priority radio HTML (Cap 4 一半)
#
# 這個腳本補完：
#   - Cap 6: migrate.ts 註冊 021
#   - Cap 4: booking.html submit body 加 commissionPct
#   - 然後 commit / push / VPS deploy / 跑 migrations / restart
# ============================================================
set -euo pipefail

REPO="$HOME/Desktop/HualienTaxiServer"
SSH_KEY="$HOME/Desktop/LightsailDefaultKey-ap-northeast-5.pem"
VPS="ubuntu@15.164.245.47"

cd "$REPO" || { echo "❌ 找不到 $REPO"; exit 1; }

# === Cap 6: 補 migrate.ts 註冊 021 ===
if ! grep -q "fix-missing-cols" src/db/migrate.ts; then
  echo "🔧 補 migrate.ts 註冊 021..."
  # 在 commission-defaults 那行下面加
  awk '
    /commission-defaults.*020/ {
      print
      print "  '\''fix-missing-cols'\'': '\''021-fix-missing-columns.sql'\'',             // 補 fcm_token / phone_or_line_id 等遺漏欄位"
      next
    }
    { print }
  ' src/db/migrate.ts > /tmp/migrate.ts.new && mv /tmp/migrate.ts.new src/db/migrate.ts
  echo "  ✓ migrate.ts 已更新"
else
  echo "  ✓ migrate.ts 已含 021，跳過"
fi

# === Cap 4: 補 booking.html body 加 commissionPct ===
if ! grep -q "commissionPct," public/liff/booking.html; then
  echo "🔧 補 booking.html 加 commissionPct 進 body..."
  # 在 paymentType, subsidyType, 之後加 commissionPct,
  # 用 sed 的 -i 跨平台寫法（macOS BSD sed）
  sed -i.bak 's|        subsidyType,|        subsidyType,\
        commissionPct,|' public/liff/booking.html
  rm -f public/liff/booking.html.bak
  echo "  ✓ booking.html body 已加 commissionPct"
else
  echo "  ✓ booking.html 已含 commissionPct，跳過"
fi

# === Build 兩端 ===
echo "🔨 本地 typecheck server..."
pnpm tsc --noEmit
echo "🔨 admin build..."
( cd admin-panel && pnpm build > /dev/null 2>&1 && echo "  ✓ admin build OK" )

# === Commit + push ===
echo "📦 git commit + push..."
git add -A
git commit -m "feat(audit): GoGoCha 6 Cap 補強 — commission 預設 + Drivers.tsx Partner UI + 自動 re-queue + LIFF priority + backfill + 既有 bug

[Cap 1] partners.default_order_commission_pct + orders DEFAULT 5；接單時用司機 partner default 覆蓋
[Cap 2] Drivers.tsx 編輯 modal 加 3 個 Partner select (PRIMARY_FLEET/BRAND/RECRUITED_BY)
[Cap 3] PATCH /status DONE 後檢查 GPS 仍在 dispatched_from_zone 內就自動 re-queue
[Cap 4] LIFF booking.html 加 priority radio + commissionPct 進 body（4 段 0/5/10/20）
[Cap 5] scripts/backfill-billing.ts 補歷史訂單 snapshot
[Cap 6] Migration 021 補 drivers.fcm_token + customer_notifications.phone_or_line_id

部署後跑：
  pnpm migrate commission-defaults
  pnpm migrate fix-missing-cols
  （可選）pnpm ts-node scripts/backfill-billing.ts --apply

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
git push origin main
echo "  ✓ pushed"

# === VPS 部署 ===
echo "🚀 VPS 部署..."
ssh -i "$SSH_KEY" -o StrictHostKeyChecking=no "$VPS" "
  source ~/.nvm/nvm.sh
  cd /var/www/taxiServer
  git pull origin main
  pnpm install --silent
  pnpm build
  cd admin-panel && pnpm build > /dev/null 2>&1 && cd ..
  npx ts-node src/db/migrate.ts commission-defaults
  npx ts-node src/db/migrate.ts fix-missing-cols
  pm2 restart taxiserver
  pm2 list 2>&1 | grep taxiserver | head -1
"

echo ""
echo "🎉 全部完成！"
echo ""
echo "可選：跑 backfill 補歷史 snapshot（必須在 VPS）"
echo "  ssh -i $SSH_KEY $VPS 'cd /var/www/taxiServer && npx ts-node scripts/backfill-billing.ts'"
echo "  確認 dry-run 結果 OK 再加 --apply"
