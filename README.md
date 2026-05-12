# ⬛ EdgeBar

> Gesture bar siêu nhẹ cho Android — hoạt động trên Lock Screen nhờ `TYPE_ACCESSIBILITY_OVERLAY` + `FLAG_SHOW_WHEN_LOCKED`

---

## Cơ chế hoạt động (giống Bottom Quick Settings của Tom Bayley)

```
AccessibilityService
    └─ WindowManager.addView(overlay, params)
            params.type  = TYPE_ACCESSIBILITY_OVERLAY   ← key #1: chỉ loại này vượt qua lock screen
            params.flags = FLAG_NOT_FOCUSABLE            ← không steal focus
                         | FLAG_NOT_TOUCH_MODAL          ← touch ngoài vùng vẫn pass-through
                         | FLAG_SHOW_WHEN_LOCKED         ← key #2: sống trên lock screen!
                         | FLAG_LAYOUT_IN_SCREEN         ← fill cả insets/notch
```

Overlay này **trong suốt**, chỉ cảm nhận touch — không block UI, không hiển thị bất cứ thứ gì (tuỳ chọn hiện thanh mờ nhạt để định vị).

---

## Build & Install

### Yêu cầu
- Android Studio Hedgehog+ hoặc command line với JDK 11+
- Android 8.0+ (minSdk 26) — pixel 2XL chạy Android 11 ✅

### Build
```bash
cd EdgeBar
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Bật AccessibilityService (2 cách)

**Cách 1 — UI (chuẩn):**
1. Mở app EdgeBar
2. Nhấn "Mở Accessibility Settings"
3. Tìm "EdgeBar Gesture Bar" → bật

**Cách 2 — ADB (không cần chạm màn hình):**
```bash
adb shell settings put secure enabled_accessibility_services \
  com.edgebar.app/.EdgeBarService

adb shell settings put secure accessibility_enabled 1
```

> **Lưu ý Pixel 2XL Android 11:** Cách ADB đặc biệt hữu ích vì một số ROM bị bug UI accessibility.

---

## Cấu hình Gesture → Action

Mở app → gán action cho từng cử chỉ:

| Cử chỉ | Mô tả |
|---------|-------|
| Chạm 1 lần | Single tap trên thanh |
| Chạm 2 lần | Double tap (< 280ms) |
| Giữ lâu | Long press (500ms) |
| Vuốt lên/xuống/trái/phải | Swipe ≥ 80px |

### Actions có sẵn

| Action | API | Ghi chú |
|--------|-----|---------|
| Tắt màn hình | `GLOBAL_ACTION_LOCK_SCREEN` (API 28+) | Android 11 ✅ |
| Bật/Tắt đèn pin | `CameraManager.setTorchMode()` | Không cần quyền CAMERA cho torch |
| Camera an toàn | `INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE` | Mở camera từ lock screen, không unlock |
| Back / Home / Recents | `performGlobalAction()` | Built-in |
| Kéo thông báo | `GLOBAL_ACTION_NOTIFICATIONS` | |
| Quick Settings | `GLOBAL_ACTION_QUICK_SETTINGS` | |
| Chụp màn hình | `GLOBAL_ACTION_TAKE_SCREENSHOT` | API 28+ |
| Chia đôi màn hình | `GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN` | |
| Âm lượng +/- | `AudioManager.adjustStreamVolume()` | |
| Intent tùy chỉnh | `startActivity(intent)` | Nhập package name trong app |

---

## Tuỳ chỉnh giao diện thanh

| Tuỳ chọn | Mô tả | Mặc định |
|----------|-------|---------|
| Vị trí | Bottom / Left edge / Right edge | Bottom |
| Độ dày | 4–64 dp | 24 dp |
| Chiều rộng | 10–100% màn hình | 100% |
| Hiện indicator | Thanh trắng mờ 20% để thấy vị trí | Bật |
| Haptic | Rung khi nhận cử chỉ | Bật |

---

## So sánh với các app tương tự

| | EdgeBar | Bottom Quick Settings | Touch the Notch | UbikiTouch |
|--|--|--|--|--|
| Overlay type | `TYPE_ACCESSIBILITY_OVERLAY` | `TYPE_ACCESSIBILITY_OVERLAY` | `TYPE_ACCESSIBILITY_OVERLAY` | Khác |
| Lock screen | ✅ | ✅ | ✅ | ❌ |
| Gesture 1 nhịp | ✅ | ❌ (cần kéo panel) | ✅ | ✅ |
| Vị trí | Bottom/Edge | Bottom | Top (notch) | Cạnh |
| Mã nguồn mở | ✅ (repo này) | ❌ | ❌ | ❌ |
| Kích thước APK | ~200KB | ~2MB | ~3MB | ~5MB |

---

## Câu hỏi thường gặp

**Q: Overlay không hiện trên lock screen?**  
→ Chạy lại lệnh ADB bật service. Một số ROM (MIUI, OneUI) chặn `FLAG_SHOW_WHEN_LOCKED` với app thường — `TYPE_ACCESSIBILITY_OVERLAY` là workaround duy nhất.

**Q: Tắt màn hình không hoạt động?**  
→ `GLOBAL_ACTION_LOCK_SCREEN` yêu cầu API 28+. Android 11 (API 30) trên Pixel 2XL ✅.

**Q: App có đọc nội dung màn hình không?**  
→ Không. `accessibilityEventTypes` trong config được đặt để nhận event nhưng `onAccessibilityEvent` không xử lý gì cả. Overlay chỉ nhận touch event trực tiếp.

**Q: Pin có tốn nhiều không?**  
→ Rất ít. App không có background service riêng, không poll bất kỳ thứ gì. AccessibilityService chỉ active khi có touch trong vùng overlay.

---

## Cấu trúc source

```
EdgeBar/
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/edgebar/app/
│   │   ├── EdgeBarService.java   ← Core: overlay + gesture detection
│   │   ├── MainActivity.java     ← Config UI
│   │   ├── ActionType.java       ← Action constants
│   │   └── BootReceiver.java     ← Auto-restart after reboot
│   └── res/
│       ├── xml/accessibility_service_config.xml
│       └── values/{strings,themes}.xml
└── README.md
```

---

*Inspired by Bottom Quick Settings (Tom Bayley) & Touch the Notch*
