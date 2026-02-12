# Gatling 說明

> 專案目錄：`C:\Users\suppo\Desktop\Gatling\gatling-demo`

## 專案結構

- `pom.xml`：Maven 設定
- `src/test/scala/computerdatabase/BasicSimulation.scala`：主要 Simulation
- `src/test/scala/Engine.scala`：IDE 執行入口
- `src/test/scala/Recorder.scala`：Gatling Recorder
- `src/test/resources/gatling.conf`：Gatling 設定
- `target/gatling/*/index.html`：報告輸出

## 執行方式

1) 進入專案目錄
```
cd C:\Users\suppo\Desktop\Gatling\gatling-demo
```

2) 執行 Simulation
```
mvn gatling:test
```

3) 指定 Simulation
```
mvn gatling:test -Dgatling.simulationClass=computerdatabase.BasicSimulation
```

4) 參數化（使用者重複次數 / 每次迭代最短秒數）
```
mvn "-Drun.repeat=1" "-Drun.onceSec=2" gatling:test
```

## 常見問題

- **mvn 無法在 VS Code 使用**
  - 確認 Maven `bin` 已加入 PATH

- **PowerShell 找不到 mvn**
  - 檢查 PowerShell profile 是否設定 PATH
  - 參考：`C:\Users\suppo\OneDrive\Documents\WindowsPowerShell\profile.ps1`

- **JDK 版本**
  - 建議使用 **JDK 17**
  - 例如：`C:\Users\suppo\jdk-17\jdk-17.0.17+10`

- **VS Code Metals/Bloop 問題**
  - 可執行：`Metals: Reset Workspace` 後再 `Import Build`

## CDN 判斷與執行方式

### CDN 判斷原理（實務慣例）
業界通常拆成兩個狀態來看：

1) **cdn_in_path（有經過 CDN/WAF）**
   - DNS CNAME 指向 CDN 供應商（例如 Imperva）
   - 或回應 header 出現供應商特徵（例如 `X-CDN`, `X-Iinfo`, `CF-Ray`）

2) **cdn_cache_hit（有命中快取）**
   - `Age > 0`
   - `X-Cache` 含 `HIT` 或 `X-Cache-Hits > 0`
   - `CF-Cache-Status: HIT`

> 沒有「單一標準」必須用哪一種，實務上會同時輸出這兩個狀態，避免混淆。

### Gatling 已加入的 CDN 檢查
檔案位置：`gatling-demo/src/test/scala/computerdatabase/BasicSimulation.scala`

啟動後 console 會印出：
- `[CDN] ...header 值...`
- `[CDN] cdn_in_path=true/false, cdn_cache_hit=true/false, strictHit=true/false`

其中：
- `cdn_in_path`：是否經過 CDN/WAF
- `cdn_cache_hit`：是否命中快取
- `strictHit`：目前設定為 `cdn_in_path && cdn_cache_hit`（WAF in path ≠ cache hit）

### 判斷流程圖（怎麼看有抓到 CDN）
```
Request_1 (warm) ──▶ Request_1 (cdn)
                     │
                     ├─ 讀取回應 header（X-CDN / X-Iinfo / Age / X-Cache…）
                     │
                     ├─ cdn_in_path = (有 CDN/WAF header) ? true : false
                     │
                     ├─ cdn_cache_hit = (Age>0 或 X-Cache HIT…)? true : false
                     │
                     └─ strictHit = cdn_in_path AND cdn_cache_hit
```

### CDN Log（本機）
**只在 `strictHit=false` 時寫入**：
`gatling-demo/target/gatling/cdn.log`

格式為 JSON Lines，例如：
```
{"ts":"2026-02-10T03:23:03.123Z","request":"request_1_cdn","url":"https://www.starlux-airlines.com/","status":200,"response_time_ms":123,"cdn_in_path":true,"cdn_cache_hit":false,"strict_hit":true,"headers":["X-Iinfo=...","X-CDN=Imperva"]}
```
若要改成「每次都寫」，把 `if (!strictHit)` 這段判斷拿掉即可。

### CNAME 檢查（確認是否走 CDN）
```
Resolve-DnsName www.starlux-airlines.com -Type CNAME
```
若回傳 `*.incapdns.net`，代表走 Imperva CDN/WAF。

### 注意事項
- Gatling 報告本身不會顯示 HTTP header，需看 console 輸出。
- 若要嚴格「快取命中才算 CDN」，可把 `strictHit` 改成只看 `cdn_cache_hit`。
