# rEconomy

Paper plugin: Vault coins, homes, TPA, teams/claims, auction house, wagers, coinflip, government treasury, paid RTP, decorative heads shop, name colors, image maps, living-shop CSV hooks, leaderboards, vanish, and `/guide`.

## Requirements

- Paper 1.21+
- MySQL
- Vault or VaultUnlocked
- Optional: PlaceholderAPI, UltimateShop (living shop), zHead (`/heads` — compile stubs included; install zHead at runtime)

## Setup

1. Build with Gradle (`./gradlew shadowJar` or `build`).
2. Drop the jar into `plugins/`.
3. Edit `plugins/rEconomy/config.yml` — set MySQL to your own host/db/user/pass.
4. Restart.

Default MySQL values are placeholders (`127.0.0.1` / `CHANGE_ME`).

## Credits

Invisible item frames behaviour includes code ported from [Simple-Frames](https://github.com/iSlavok/Simple-Frames) (MIT © iSlavok).
