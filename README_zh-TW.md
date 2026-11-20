# Homestead_Mod

基於官方 [Homestead](https://github.com/Sarg338/Homestead)（v2.5.0）之深度增強客製化版本，專為 Fire TV / Panasonic 智慧電視量身打造，加入全功能遙控器巨集、獨立長短按自訂、即時影音解碼浮窗 HUD 與完整繁體中文化。

專為 Fire TV 打造的替代首頁螢幕（替代啟動器）：沒有廣告橫幅、沒有自動播放的預告片、沒有商業贊助推薦。只有您的輸入訊號源與應用程式，其餘雜質一概不存。

它同時取代了遙控器上的 Home（主頁）**按鍵**，這也是最困難的關鍵 —— Fire OS 官方將 Amazon 啟動器強制鎖定為系統預設的主頁活動（Home Activity），嚴格禁止其他第三方啟動器取而代之。

<img src="screenshots/before_after.png" alt="對比圖：左為 Amazon 原廠首頁，充斥贊助橫幅、推薦節目列，應用程式被擠在窄列中；右為 Homestead，HDMI 輸入訊號源置頂、所有 App 統一整齊排列在網格中，並保留 Amazon Home 圖塊。">

兩張截圖來自同一台電視，拍攝時間僅相隔數分鐘。

---

## 🌟 Homestead_Mod 專屬新增功能特色

1. **Panasonic 電視資訊快速直達巨集（`screen_display`）**：
   - 將螢幕資訊鍵（`VID_SRC_INFO / 334`）預設綁定為自動化 ADB 巨集（`input keyevent 176 && sleep 0.28 && input keyevent 20 20 20 20 20 20 20 && sleep 0.08 && input keyevent 23`，極速 7 次向下直達，無分段秒開）。
   - 一鍵秒開電視原廠畫面顯示資訊（解析度、更新率、Dolby Vision / Atmos 狀態），免去手動按選單多層翻找之苦。

2. **即時影音資訊浮窗 HUD（Live Media HUD / `info_hud`）**：
   - 隨時可在任何第三方影視 App（YouTube、Prime Video、Netflix、Disney+ 等）或 HDMI 訊號源上呼叫獨立浮窗：
     - 📱 **應用**：當前執行的 App 名稱。
     - 📺 **輸出**：當前系統輸出解析度與更新頻率。
     - ⚡ **幀率**：當前即時顯示刷新率（FPS）。
     - 🚀 **網速**：即時 EMA 平滑網絡帶寬即時下載速率。
     - 🌈 **色彩**：即時精準判斷 SDR / HDR10 / HDR10+ / Dolby Vision / HLG。
     - 🎬 **視訊**：底層真實硬體解碼器視訊編碼（AVC / HEVC / VP9 / AV1 / VVC）。
     - 🔊 **音訊**：即時動態音訊輸出格式判斷（PCM / Dolby Digital / DD+ / Atmos / DTS 等，杜絕假 Atmos）。

3. **全按鍵獨立短按與長按（Hold 0.45s）自訂配置**：
   - 全面支援頻道上/下鍵（CH+ / CH-）、四色鍵（紅/綠/黃/藍）、影視串流鍵（APP 1-4）、數字鍵（0-9）、資訊鍵。
   - 每一顆按鍵均可獨立配置「短按（點擊）」與「長按（按住 0.45 秒）」執行不同動作或直接啟動指定 App。

4. **放開按鍵才觸發機制（`ACTION_UP`）防重複**：
   - 短按動作統一於按鍵放開時（`ACTION_UP`）執行；若已長按逾時觸發長按動作，放開時會自動壓制短按，徹底防止長按短按重複觸發連擊。

5. **應用程式自然字母排序（A–Z）**：
   - 採用 `java.text.Collator.getInstance(Locale.getDefault())`，支援繁體中文筆畫/注音與英文字母自然混排排序，解決原本隨機排序不易查找的問題。

6. **專屬遙控器設定 UI 與按鍵診斷模式**：
   - **按鍵偵測與診斷**：提供安全按鍵測試頁面，即時捕捉按鍵、驗證 Homestead 是否成功攔截、辨識是否為電視韌體寫死按鍵，並提供「測試執行此動作」即時驗證按鍵對應效果。
   - **亮度控制**：支援頻道鍵快速調整螢幕亮度，並提供系統亮度調整與全局覆蓋濾鏡雙模式。

7. **完整繁體中文（zh-TW）在地化支援**：
   - 全介面繁體中文在地化與中文說明文件，並同步合併官方最新 2.5.0 核心更新（納入 Spanish 與螢幕鏡像支援）。

---

## 功能特色

- **純淨無廣告的首頁螢幕**：將您的訊號輸入、電視應用程式與其他 App 整理為三大區塊。完全沒有贊助廣告列、沒有自動播放的影片、沒有強推圖塊。可在「設定」→「首頁螢幕」中的**橫列順序**自由調整排列順序（例如將電視 App 置頂）。
- **兩種設計風格**（設定 → 首頁螢幕 → 設計）：
  - **經典風格（Classic）**：如上圖所示的預設經典佈局。
  - **底欄風格（Dock）**：將最常用的電視 App 以 dock 欄形式置於首頁底部，聚焦選取時帶有該 App 主題色的光暈效果，其餘 App 則收納於下方網格中；頂部狀態列顯示時鐘與設定快捷鍵。可透過**底欄圖塊大小**調整圖塊尺寸。
- **Home 鍵直達**：按下遙控器 Home 鍵後，約 170 毫秒即可瞬間切換至 Homestead，不再跳回臃腫的 Amazon 原廠首頁。
- **HDMI 與 AV 端子輸入圖塊**：電視訊號源以獨立圖塊呈現，按一下即可直接切換輸入源。
- **即時電視（Live TV）圖塊**：針對內建數位電視調諧器的電視機型，在 Amazon Live TV 啟用時會自動顯示，點選即可直接播放上次收看的頻道。啟用**即時電視優先**（設定 → 首頁螢幕）後，會在頂部以大型圖塊呈現，且每次按 Home 鍵都會自動聚焦該圖塊。
- **智慧排序與釘選**：可依「電視本機使用頻率統計」或「A–Z 字母順序」排列應用程式。在任一圖塊上按遙控器 **MENU（選單）** 鍵（或長按選取鍵）即可開啟選單，選擇**釘選至最前**或**移動**位置。已釘選的圖塊會優先排列在橫列最前方，其餘圖塊則維持原有排序。
- **隱藏不需要的內容**：透過同一個圖塊選單，可隨時隱藏任何不想看到的 App 或項目。
- **豐富的背景設定**（設定 → 背景）：
  - 預設深藍漸層、純黑背景（極適合 OLED 螢幕與夜間低光源觀看）。
  - 八款內建**風景桌布**（四款精緻手繪插畫、四款真實高畫質相片）。
  - 支援[自訂個人背景相片](#自訂背景相片)。
  - 支援 **Bing 每日圖片**（選用功能，會依電視地區或指定國家市場每日自動更新並支援離線快取）。
- **豐富的自訂選項**（設定 → 首頁螢幕）：
  - 頂部問候語與日期顯示。
  - 非電視版 App 顯示為寬圖塊。
  - 將所有 App 合併為單一網格，不再區分電視版與非電視版。
  - 支援 Fire TV 原廠圖示風格（Amazon 首頁、設定與電視健康度）。
- **快速開啟 Fire TV 原廠系統設定**（設定 → Fire TV 設定）：網路、遙控器與藍牙、應用程式管理、我的 Fire TV、螢幕鏡像（Display mirroring）、設備控制、說明等，皆可直接從 Homestead 快速啟動，並可新增捷徑至首頁或頂部狀態列。
- **多國語言支援**：提供繁體中文（Traditional Chinese）、英文（English）、法文（French）、德文（German）與西班牙文（Spanish），預設跟隨電視系統語言，亦可在「設定」→「語言」中自由切換。
- **半透明設定面板**：設定選單以覆蓋層展開於首頁之上，當您調整背景、欄數、時鐘或版面時，可即時在後方查看變化效果。
- **電視健康度（TV Health）**：一目了然電視當前的運作狀態（動畫速度、廣告追蹤設定、螢幕保護程式、後台正在執行的 Amazon 遙測數據收集程式、可用記憶體與儲存空間）。所有標記為可最佳化的項目均可一鍵修復。
- **系統精簡（Debloat）**：可安全停用數十個經嚴格審查的 Amazon 預載後台服務，每項服務皆附帶白話功能說明。**絕不解除安裝、絕不刪除任何使用者資料**，所有被停用的服務隨時可在同一頁面一鍵還原。少數受 Fire OS 系統底層保護的關鍵套件會自動跳過。在支援的電視型號上（非 Fire OS 7），甚至可以安全停用 Amazon 桌面啟動器本體（標記為進階風險選項，需確認兩次）。
- **無需電腦即可完整設定**：透過內建的本機 ADB 通道隧道（On-device tunnel），僅憑遙控器即可在電視上啟用 Home 鍵接管、自動授予必要權限並執行系統精簡。隧道僅在執行操作時啟動，作業完成後立即自動關閉。亦可使用電腦命令列完成相同設定。
- **內建自動檢查更新**：每日自動檢查本專案 GitHub Releases 最新版本，僅在您主動點選「立即安裝」時才會更新，且強制校驗必須具備相同的簽名金鑰。
- **[簡易模式（Simple mode）](#為長輩或他人設定簡易模式)**：專為長輩或僅需單純看電視的使用者設計 —— 隱藏設定與系統維護入口、停用長按與選單隱藏圖塊功能，且完全關閉更新彈窗通知。

---

## 已驗證設備

以下均在真實實體硬體上測試驗證，非模擬器環境：

| 裝置型號 | 設備代號 | Fire OS 版本 | 驗證成果 |
|---|---|---|---|
| Insignia F30-Series Fire TV Edition | `AFTDCT31` | 7.7.1.5 | **全功能通過**：啟動器、Home 鍵接管、HDMI/AV 端子圖塊、設定選單、電視健康度、系統精簡、免電腦本機設定 |
| Amazon Fire TV Stick | `AFTSSS` | 7.7.1.6 | **免電腦設定通過**：金鑰授權、權限賦予、通道自動關閉 |
| Amazon Fire TV Stick 4K (第 2 代) | `AFTKM` | 8.1.8.5 | **使用者回報**：Home 鍵、系統精簡（停用 28 個套件；Fire OS 8 受保護項目略多於 Fire OS 7）、多次重開機測試、從電腦傳送背景照片（v1.2.9，透過電腦設定） |
| Hisense 65U65SFC Pro (Fire TV Edition) | `AFTCIBFBD1` | 8.1.8.0 | **使用者回報**：Home 鍵、系統精簡、完整重開機、套件事件修復（v1.2.3 及後續版本，透過電腦設定） |
| TCL 40V5D (Fire TV Edition) | `AFTTIFB900` | 14 (組建 SL14SHIP1021.86N) | **使用者回報**：Home 鍵、系統精簡、電視健康度、完整重開機、HDMI/AV 端子圖塊（透過電腦設定）。支援 Amazon 官方啟動器停用與啟用，重開機後 Home 鍵可直達 Homestead（v1.6.11） |
| Panasonic 旗艦 OLED 電視 (TV-65Z95BGT) | `AFTGOLDFF` | 8.1.8.5 (組建 RS8185/3896) | **使用者實測全功能通過**：啟動器、Home 鍵接管、免電腦本機 ADB 通道、電視健康度、系統精簡、電視原廠資訊直達巨集（Screen Display）、全按鍵獨立短按與長按（Hold 0.45s）、即時影音解碼浮窗 HUD 與完整繁體中文支援。 |

前兩款執行 Android 9，由本專案維護者親自測試；後三款為社群使用者實測回報（兩款為 Fire OS 8 / Android 11，一款為 Fire OS 14）。其他未列出的 Fire TV 裝置亦普遍可正常執行。

系統精簡清單已在 Insignia 電視上逐一驗證。若您的電視沒有清單中的某些套件，會自動顯示為「不存在」並略過；未包含在清單中的系統套件可透過「瀏覽所有系統應用程式」檢視。若您的電視套件命名有所不同，歡迎提交 Issue 並附上型號與 `adb shell pm list packages -s` 的輸出。

---

## 安裝方式

從 [最新版本發布頁面 (Releases)](https://github.com/czh00/Homestead_Mod/releases/latest) 下載 `homestead.apk` 並進行側載：
- 可使用電腦執行 `adb install homestead.apk`。
- 或在電視上下載「Downloader」應用程式直接下載安裝。

當 Homestead 主畫面首次開啟時，會連線檢查更新（每天最多一次，且在[簡易模式](#為長輩或他人設定簡易模式)下絕不自動檢查）。若有新版本，會提示一次，提供**立即安裝**、**稍後再說**與**略過此版本**。您也可以隨時在「設定」→「檢查更新」中手動檢查。未經您的確認，絕不會自動安裝更新。

若未使用內建本機通道，更新時會走 Fire OS 原廠安裝器，需預先允許 Homestead 安裝未知應用程式：電視「設定」→「設備與軟體」（部分型號為「我的 Fire TV」）→「開發人員選項」→「安裝未知應用程式」→「Homestead」設為開啟。

---

## 初始設定教學

由於 Fire OS 的「設定」→「無障礙功能」中隱藏了第三方應用程式，無法直接在系統選單勾選，因此接管 Home 鍵必須透過 **ADB**（電視除錯連線）來啟用。您可以使用電視本機免電腦完成，也可以從電腦命令列設定。若未設定 ADB，Homestead 仍可作為一般 App 從 Amazon 應用列中開啟，只有「Home 鍵直達」需要此步驟。

無障礙服務（Accessibility Service）的作用是感知使用者何時按下了 Home 鍵。它僅監控哪一個應用程式切換到了前台，**完全無法讀取螢幕內容，亦不攔截其他無關按鍵**。

### 方法一：電視本機設定（免電腦）

1. 開啟電視「設定」→「我的 Fire TV」→「開發人員選項」中的 **ADB 偵錯**。若未看見開發人員選項，請開啟「我的 Fire TV」→「關於」，對著設備名稱連按 7 次「選取鍵」即可解鎖。
2. 從 Amazon 應用列中開啟 **Homestead**。首次執行的設定精靈會引導您完成其餘步驟。（之後亦可在「設定」圖塊 →「設定」中重新執行）。
3. 選擇**進行設定**（或設定選單中的**本機通道（進階）**），並接受 Android 的本機 VPN 授權提示（在提示視窗中按「向下鍵」選取「確定」）。
4. 電視畫面上會跳出是否允許此 ADB 金鑰的詢問視窗。請確認畫面上顯示的指紋碼與 Homestead 提示一致，勾選**永遠允許來自這部電腦的連線**，然後選取「確定」。（務必勾選，否則每次重新連線都會再次彈窗詢問）。
5. 設定精靈將自動啟用 Home 鍵轉向。在設定選單中選擇**自動完成設定**，系統會同時授予電視健康度、系統精簡與 Home 鍵所需的權限。
6. 按下遙控器上的 **Home** 鍵即可享受純淨首頁！

本機通道僅在應用程式變更系統設定時暫時啟動，作業完成後便會自動關閉。[SECURITY.md](SECURITY.md) 詳細說明了 Homestead 存取的權限與網路傳輸範圍。

部分 Amazon 套件在電視重新開機後會自動重新啟用。在開啟**重開機後重新套用**（預設開啟）的情況下，Homestead 會在開機約 1 分鐘後自動將其再度停用（這需要保持 ADB 偵錯為開啟狀態）。若系統韌體更新重新啟用了某些套件，開啟**系統還原精簡時發出警告**後，主畫面會顯示 10 秒提示圖塊，讓您一鍵再度關閉。

若未來因為韌體更新導致 Home 鍵失效，只需再次進入設定並點選「自動完成設定」即可修復。

### 方法二：透過電腦命令列設定

將電腦與電視連接至同一區域網路，執行以下指令：

```sh
adb connect <電視的IP位址>          # IP 可在 App 內的指令說明頁面中查看
adb shell settings put secure enabled_accessibility_services com.homestead.launcher/com.homestead.launcher.HomeRedirectService
adb shell settings put secure accessibility_enabled 1
adb shell pm grant com.homestead.launcher android.permission.WRITE_SECURE_SETTINGS
adb shell pm grant com.homestead.launcher android.permission.READ_LOGS
```

前兩行指令用於啟用 Home 鍵接管服務；第三行允許「電視健康度」調整系統設定；第四行（`READ_LOGS`）能讓 Homestead 第一時間捕捉到 Home 鍵訊號，徹底消除 Amazon 原廠首頁的瞬間閃爍，並確保在 Amazon 原廠首頁停留時也能順利按 Home 鍵返回 Homestead。

若要透過電腦批次停用 Amazon 預載套件，可在 Homestead 內開啟「電腦指令」頁面，即可取得適用於您電視機型的完整指令列表。

---

## 自訂背景相片

在「設定」→「背景」→「風景」中收錄了 8 張精美內建桌布：4 張手繪插圖與 4 張高畫質攝影相片（版權與來源請參閱 [LANDSCAPES.md](LANDSCAPES.md)）。點選即可立即套用，並支援**相片暗角/變暗（Photo dimming）**效果以確保文字清晰可讀。

### 從電視本機設定（免電腦）

將 JPEG 或 PNG 圖檔儲存至電視的 `Download` 資料夾（可透過 Downloader App 或 Silk 瀏覽器下載至該處）。隨後開啟「設定」→「背景」→**選擇此電視上的相片**，點選該圖片即可套用為背景。Homestead 會透過本機 ADB 通道讀取該圖片，因此無需額外的檔案存取權限。

*註：檔名若包含英文字母、數字、空格、點、破折號、括號或 `+` 以外的特殊字元將會自動略過。*

### 從電腦傳送設定

可由電腦透過兩道 ADB 指令將圖片傳入電視並套用：

```sh
adb push background.jpg /sdcard/Download/
adb shell "content write --uri content://com.homestead.launcher.background/photo < /sdcard/Download/background.jpg"
```

完成後，至「設定」→「背景」，在清單中選取「相片」即可。無論任何解析度或比例的圖片皆會自動縮放並裁切以填滿螢幕，且僅保留螢幕適應副本以節省記憶體空間。

- **更換相片**：再次執行上述兩行指令，於下次按下 Home 鍵時即刻更新。
- **清除相片**：`adb shell content delete --uri content://com.homestead.launcher.background/photo`，背景將恢復為純黑。
- 請依上述指令在電視端執行 redirection，避免直接在 Windows PowerShell 中透過管道輸入（在測試中發現 Windows 管道傳輸可能造成圖檔損毀）。

解除安裝 Homestead 時，個人相片複本將會一併刪除。

---

## 為長輩或他人設定：簡易模式

若電視是為長輩、小孩或僅希望單純開機看影片的使用者設定，可在初次設定時選擇**簡易模式（Simple mode）**（日後亦可在設定中切換）：

- **隱藏設定與電視健康度圖塊**：避免誤觸設定。若需進入設定，在主畫面遙控器上**連續按 5 次選單鍵（MENU ☰）**即可開啟。設定精靈會在完成前讓您練習一次。
- **停用隱藏圖塊功能**：停用 MENU 鍵與長按選取鍵的「隱藏圖塊」動作，防止不小心將常用 App 隱藏。可在開啟簡易模式前先將不需要的項目隱藏好。
- **完全關閉更新彈窗**：Homestead 不會主動彈窗提示更新，由您探訪時在設定中手動檢查更新。
- **一鍵套用安全精簡**：在初始設定精靈中勾選簡易模式時，會自動關閉安全清單中的廣告、遙測追蹤、使用教學與展示內容；保留 Amazon Photos（方便瀏覽家庭相簿）與 Alexa（語音搜尋對長輩通常比遙控器打字更輕鬆）。

**交付電視前的注意事項：**
- 若日後因系統重大韌體更新導致 Home 鍵跳回原廠，電視畫面上會自動彈出「完成設定」導引頁面，這代表需要重新執行一次快速設定。
- 部分 Android 系統在數天未使用 ADB 後可能遺忘「永遠允許」的金鑰。若「重開機後重新套用」需要自動關閉套件，電視可能會再度彈出 ADB 授權視窗（在 Fire OS 7 上未曾發生此現象）。

---

## 徹底還原與解除安裝

1. 開啟 **系統精簡（Debloat）**，點選 **還原此應用程式關閉的所有項目**。這會將先前被 Homestead 停用的所有 Amazon 套件全數重新啟用。*（直接解除安裝 Homestead 不會自動復原套件狀態；若您停用了 Amazon 原廠啟動器，務必先執行此步驟，否則系統將暫時失去桌面！）*
2. 若曾使用本機通道，在「設定」中選擇「清除金鑰」，接著在電視「設定」→「我的 Fire TV」→「開發人員選項」中撤銷 ADB 授權，並關閉 ADB 偵錯。
3. 至電視「設定」→「應用程式」→「管理已安裝的應用程式」中正常解除安裝 Homestead。

解除安裝會自動移除無障礙服務，Home 鍵將立即回歸 Amazon 原廠主頁。

**若想保留 Homestead 但將 Home 鍵交還原廠**：只需在設定中選取「關閉 Home 鍵功能」（需按兩次確認）。Homestead 將作為一般 App 保留於電視中。

---

## Home 鍵接管原理

Fire OS 嚴禁任何第三方應用程式註冊為系統預設主頁。Homestead 巧妙結合了 Android 無障礙服務（Accessibility Service）與日誌監控機制：

1. **無障礙視窗覆蓋（Overlay）**：Fire OS 在使用者按下 Home 鍵後，會刻意對其他 App 的啟動加入數秒延遲。但 Homestead 透過無障礙浮層視窗（Accessibility Overlay）展示首頁，該視窗完全不受此延遲機制限制，能瞬間覆蓋螢幕。待系統延遲時間結束後，再無縫切換為正式的 Activity 畫面，無縫維持選取狀態與滾動位置。
2. **極速捕捉與消除閃爍（`READ_LOGS`）**：一般無障礙事件在 Amazon 啟動器開始繪製畫面時才會收到通知（已有數百毫秒的肉眼可見延遲）。然而在系統啟動 Amazon 桌面之初，系統日誌（Logcat）便會率先寫入紀錄。Homestead 透過獨立的輕量背景程序（`:homekey`）即時監控該行日誌，在 Amazon 原廠畫面尚未渲染前，搶先將覆蓋視窗升起，達到無閃爍的流暢過渡。

---

## 已知限制

- **未授予 `READ_LOGS` 權限時**：按下 Home 鍵時會短暫看見 Amazon 原廠首頁閃爍；若當前已停留在 Amazon 首頁上，再次按 Home 鍵將無法跳轉（需先開啟任一 App 再按 Home）。透過本機設定或電腦指令授予權限後即可完全解決。
- **螢幕短暫黑畫面**：在離開某些大型 App 切換至 Homestead 時，可能出現不到 0.25 秒的短暫黑畫面過渡（更新後的首次觸發時間可能略長）。
- **Amazon Home 圖塊暫停 30 秒**：點選首頁上的「Amazon 首頁」圖塊會暫停接管 30 秒，方便您瀏覽原廠內容；30 秒過後 Homestead 會恢復監控。在任何時刻按下 Home 鍵皆可隨時返回 Homestead。
- **Fire OS 7 原廠設定返回行為**：在 Fire OS 7 上，原廠設定主頁實際上是原廠桌面的一個分頁。因此在某些設定頁面（如網路設定）按返回鍵時，系統可能視為返回主頁而直接進入 Homestead。若需繼續調整設定，重新開啟設定即可。

---

## 編譯與打包

若您僅需使用 Homestead，直接從 [Releases](https://github.com/czh00/Homestead_Mod/releases/latest) 下載預先簽章的 `homestead.apk` 即可。

本專案**完全不使用 Gradle**，零外部第三方函式庫依賴，整個專案在 10 秒內即可編譯完成：

```sh
./build.sh            # 編譯 + 簽章
./build.sh install    # 編譯、簽章並透過 adb install -r 安裝至設備
```

在 Windows 環境下，可直接透過 PowerShell 執行 `build.ps1`：

```powershell
powershell -ExecutionPolicy Bypass -File build.ps1            # 編譯 + 簽章
powershell -ExecutionPolicy Bypass -File build.ps1 install    # 編譯並安裝
```

兩套腳本產出的 APK 具備位元組級（Byte-for-byte）的一致性。修改任何一項建置腳本後，可透過 Git Bash 執行 `tools/test-builds.sh` 進行嚴格的交叉比對驗證。

建置時會透過 `tools/MakeLandscapes.java` 依據固定種子動態生成 4 張手繪風景桌布（`assets/landscape_drawn_*.jpg`）。

**建置環境需求**：
- JDK 11 或更新版本。
- Android SDK（`build-tools/36.1.0`、`platforms/android-34`）。
- 發布金鑰不包含在代碼庫中。`build.sh` 會讀取 `~/.homestead-keys/` 或環境變數 `HOMESTEAD_KEYSTORE` / `HOMESTEAD_KEYPASS`。
- 本專案的 ADB 模組完全由維護者參考 AOSP 官方原始碼（`system/core/adb`、`libcrypto_utils/android_pubkey.c`）獨立撰寫，未直接複製專案程式碼。

---

## 專案原始碼導覽（供開發者與 AI 助理參考）

所有程式碼皆位於 `src/com/homestead/launcher/`，各類別檔頭均有其設計思維與職責說明：

| 功能領域 | 核心類別 |
|---|---|
| **首頁螢幕** | `MainActivity`、`HomeGrid`（UI 網格，供 Activity 與無障礙浮層共用）、`AppModel`（圖塊資料模型、圖示快取、排序邏輯）、`Backgrounds`（預設背景、風景與自訂相片）、`BackgroundProvider`（經由 ADB 接收相片之 ContentProvider）、`UsageTracker`、`HdmiActivity`、`BootReceiver` |
| **Home 鍵接管** | `HomeRedirectService`（無障礙服務與浮層視窗控制；時序經過精確調校）、`HomeKeyWatcher`（日誌監聽核心） |
| **功能面板** | `Panel`（設定頁基底：支援首頁覆蓋浮層與帶說明欄的完整頁面）、`OnboardingPanel`（初次啟動導引精靈）、`SetupPanel`、`SettingsPanel`、`HealthPanel`（電視健康度）、`DebloatPanel`（系統精簡）、`CommandsPanel`、`UpdatePanel`、`PhotoPanel`（電視本機相片挑選） |
| **ADB 連線** | `AdbBridge`（單一工作執行緒、連線狀態維護、命令批次執行）、`AdbConnection`（底層通訊客戶端）、`AdbCrypto`（金鑰處理）、`TunnelVpnService` 與 `VpnSetupActivity`（本機 ADB 通道服務） |
| **系統精簡清單** | `Debloat`（系統套件目錄、絕對安全名單、即時鎖定保護規則） |
| **版本更新** | `UpdateChecker`（GitHub Repo 查詢）、`UpdateInstaller`、`UpdateFileProvider` |
| **偏好設定** | `Prefs` |
| **客製擴充模組** | `RemoteActions`（遙控器按鍵自訂對應、Panasonic 畫面顯示巨集、長短按分離防抖機制）、`InfoHud`（即時硬體解碼浮窗、幀率、網絡吞吐量、真實 HDR/音訊判斷） |

---

## 授權條款

採用 **GPL-3.0-or-later** 授權條款開源發布。請參閱 [LICENSE](LICENSE)。

由於本應用程式有權限調整電視的底層系統設定與系統服務狀態，保持強大的 Copyleft（著佐權）開源授權能確保任何發布修改版本的人，都必須公開其修改的完整原始碼。在將任何軟體安裝到電視之前，您可以清清楚楚地了解它執行的每一行程式碼與動作。
