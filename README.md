# Kayji-SimpleRewardZone

Plugin Minecraft (Paper) **sự kiện rương thịnh (reward chest)**: rương tự sinh ngẫu nhiên trên map theo lịch, có hologram + beacon beam, khu vực PvP quanh rương và GUI quản lý cho admin.

> **Tác giả:** Kayji · **Phiên bản:** 1.0.0 · **API:** 1.21 · **Java:** 21 · **Hỗ trợ Folia:** có

## Tính năng

- **Tự động sinh rương** theo lịch (`auto-spawn`, `spawn-interval-minutes`) kèm **thông báo trước** `pre-announce-seconds` và báo tọa độ (`announce-coordinates`).
- **Vùng sinh ngẫu nhiên** (`spawn-area`) — giới hạn tọa độ, số lần thử, có thể tải chunk hay không.
- **Khu vực PvP** quanh rương (bán kính, tin nhắn vào/ra, bật/tắt).
- **Hiệu ứng**: âm thanh báo trước/sinh/nhận thưởng, pháo hoa, **beacon beam** (cột sáng từ khối vàng + kính), **hologram** tên rương.
- **Hologram qua FancyHolograms** (`softdepend`) — nếu không cài, hologram tự tắt với cảnh báo trong log.
- **Template rương**: chế độ spawn `random` hoặc chọn `selected` + `spawn-template-id`; quản lý qua GUI.
- **GUI quản lý** (`/rewardzone`), lưu dữ liệu rương đang active để **giữ nguyên sau restart**.
- Tương thích **Folia** (`folia-supported: true`).

## Bảng lệnh

Gõ trong game với dấu `/`, có tab-complete (alias: `/rz`, `/thinh`):

| Lệnh | Quyền | Mô tả |
| --- | --- | --- |
| `/rewardzone` (hoặc `/rewardzone gui`) | `rewardzone.admin` | Mở GUI quản lý rương thịnh |
| `/rewardzone spawn [random\|<id>]` | `rewardzone.admin` | Thả rương ngay (ngẫu nhiên hoặc theo template) |
| `/rewardzone reload` | `rewardzone.admin` | Tải lại `config.yml` + data, dựng lại hologram và task |
| `/rewardzone despawn` (hoặc `remove`) | `rewardzone.admin` | Xóa rương đang active |

> Quyền `rewardzone.admin` — mặc định chỉ `op`.

## Cấu hình (tóm tắt)

```yaml
settings:
  world: world
  auto-spawn: true
  spawn-interval-minutes: 60     # mỗi 60 phút sinh 1 lần
  pre-announce-seconds: 30       # báo trước 30 giây
  despawn-minutes: 60            # rương biến mất sau 60 phút
  announce-coordinates: true
  spawn-template-mode: random    # random | selected

spawn-area: { min-x: -1000, max-x: 1000, min-z: -1000, max-z: 1000 }

pvp-zone:
  enabled: true
  radius: 18

beacon-beam:
  enabled: true
  base-block: GOLD_BLOCK
  glass: YELLOW_STAINED_GLASS

hologram:
  enabled: true
  visibility-distance: 96
  lines: ["#D473EFRưởng thịnh", "#B9A7F5Chắc tay phải đón"]
```

## Cài đặt

```bash
mvn clean package
```

1. Copy `target/Kayji-SimpleRewardZone.jar` vào thư mục `plugins/` của server **Paper 1.21+**.
2. (Khuyến nghị) Cài **FancyHolograms** để có hologram — không có thì plugin vẫn chạy, hologram bị tắt.
3. Chạy server một lần để tạo `config.yml`, rồi restart.

> Dependency `paper-api` (provided) và `FancyHolograms` (optional) — không đóng gói vào jar.

## Cấu trúc dự án

```
├── pom.xml                                    Maven (release 21, paper-api 1.21.8)
├── src/main
│   ├── java/me/kayji/simplerewardzone
│   │   ├── KayjiSimpleRewardZone.java         Lớp chính + /rewardzone
│   │   ├── RewardGuiManager.java              GUI quản lý
│   │   ├── RewardDataStore.java               Lưu template + rương active
│   │   ├── RewardHologramService.java         Interface hologram
│   │   ├── FancyRewardHologramService.java    Triển khai qua FancyHolograms
│   │   └── ...                                Zone/Block/Firework/Hologram helpers
│   └── resources
│       ├── plugin.yml                         Lệnh + quyền (folia-supported)
│       └── config.yml                         Vùng sinh, PvP, hiệu ứng, hologram
└── Kayji-aemcao/                              ⚠️ Subproject riêng (xem dưới)
```

### Subproject `Kayji-aemcao/`

Thư mục con là **plugin độc lập khác** (`KayjiAemCao` 1.0.0, API 1.21) — hệ thống **teleport/home**:

| Lệnh | Quyền | Mô tả |
| --- | --- | --- |
| `/tpa <player>`, `/tpahere <player>` | `kayjiaemcao.tpa` / `.tpahere` | Gửi yêu cầu dịch chuyển |
| `/tpaccept [player]`, `/tpdeny [player]`, `/tpacancel [player]` | `kayjiaemcao.tpa` | Chấp nhận / từ chối / hủy yêu cầu |
| `/home [name]`, `/sethome <name>`, `/delhome <name>`, `/homes` | `kayjiaemcao.home` / `.sethome` / `.delhome` | Quản lý home (có GUI) |
| `/back` | `kayjiaemcao.back` | Quay lại vị trí vừa chết |
| `/aemcao reload` | `kayjiaemcao.admin` | Tải lại cấu hình |

Softdepend: LuckPerms, Essentials(EssentialsX), CMI, HuskHomes, AdvancedTeleport. Build riêng bằng `cd Kayji-aemcao && mvn package`.

## Giấy phép

[GNU General Public License v3.0](LICENSE)
