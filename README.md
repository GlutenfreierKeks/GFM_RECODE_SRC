# GFM Recode

> **Open Source Proof of Concept** — Minecraft 1.21.11 Utility / Farm Macro Client (Fabric)

GFM Recode is a **proof of concept** utility client for Minecraft **1.21.11** built on
Fabric (Loom 1.15, Java 21). It started as a full recode of the "GlutenFreiKeks Farm
Macro" idea and turned into a feature-juice experiment that you can use, read, and
learn from.

## ⚠️ Disclaimer

- This is a **proof of concept** project. It was written for learning and personal use.
- A lot of this code was **vibecoded** — expect rough edges, chaos, and the occasional
  `TESTESP.java` / `TESTBLOCKESP.java` floating around. You have been warned.
- Several features were **yoinked / heavily inspired by other open source clients**
  and reimplemented to taste. Credits go to those communities for the ideas.
- Use at your own risk. The authors are not responsible for bans, bugs, or burnt PCs.

## 📜 License / Usage

The source code is open for everyone to read and use — **as long as you have no
revenue intentions**.

You may use, copy, modify, and build upon this project for **non-commercial
purposes only**. Selling, monetizing, or otherwise making money with this code
(in whole or in part) is **not allowed**. See [`LICENSE`](LICENSE) for the full terms.

## ✨ What's inside

| Area | Details |
|---|---|
| **Combat** | KillAura, AimAssist, TriggerBot, WTap, JumpReset, AutoTotem, ShieldBreaker, MaceSwap, MaceShieldBreaker, CrystalPlace, DoubleAnchor, AnchorMacro, AutoPearl, AutoWeb, HoverTotem, InteractionLock, KnockbackDisplacement, SpearSwap, SpinBot, HitCrystal |
| **Movement** | Sprint, SafeWalk, AutoWalk, WindHop, CactusTower, Clutch |
| **Render / ESP** | ESP, Tracers, ChunkFinder, ChunkDiff, StashFinder, StorageFinder, SpawnerFinder, ChestSearch, ChestCounter, BaseFinder, AncientFinder, LightFinder, AdvancedLightFinder, RedstoneFinder, BedrockFinder, BedrockHoleFinder, HoleESP, Freecam, Freelook, FullBright, ViewModel, NoRender, StatsHider, PlayerActivity, Hud, BrowserOverlayESP, SongPlayer |
| **World** | Nuker, FastPlace, HypixelPumpkin |
| **Farm / Macro** | AutoClicker, AutoAttack, AutoMine, AutoTool, FastPlacer, FarmLandMaker, CropBreaker, SeedGrower, SeedPlacer, SugarCaneFarm, AutoBerry, AutoGlowBerry, AutoMud, AutoOrder, AutoTips, AutoTrade, AutoStaffAFK, AutoTpahere, ChestSeller, InvSeller, ItemStore, ItemSteal, ItemDrop, BoneDrop, SpawnerBuy, SpawnerSell, SpawnerUpgradeAndSell, EuropeMCSpawnerBuy, SlowFizSpawnerBuy, SlowFizShopBuyer, PlatformBuilder, AntiMacro, AntiBaseLeaker, Calculator, ChatFilter, CommandExecutor |
| **Misc** | NameChanger, Notifier, DiscordPlaying (Discord RPC), SoundVolume, ToolSaver, ClientSettings |

### 🎛️ GUI & Systems

- **ClickGUI** — in-game module/settings screen with animations and custom widgets
- **Web-based UI** — a local web server (`WebUiServer`) serves an HTML GUI + HUD
  rendered through an embedded browser (HTML overlay, browser ESP overlay)
- **Config System** — save/load your settings per profile (`ConfigManager`)
- **Anti-Cheat Profiles** — pick a server profile (Vanilla / Grim / Vulcan) and
  modules get enabled, blocked, or tuned accordingly (`AntiCheatProfileManager`)
- **Notifier** — in-game notifications with animations
- **ProGuard build pipeline** — the Gradle build can obfuscate the final jar

## 🛠️ Build

```bash
./gradlew build
```

Requires JDK 21. Output lands in `build/libs/`.

## 🙏 Final words

This project exists to show what a solo "vibe + yoink" development loop can produce.
If you learn something from it, that's the whole point. If you fork it — keep it
open, keep it free, and don't make money off it. 🍪
