
## Docker Build & Run

### Build my Python environment image
`cd docker`
`docker build -t pyalgo:basic .`

### Run the container
export ALGO_HOME=/Users/elton/dev/algo-trading

`cd docker`
`docker run -ti -p 8888:8888 -v "$ALGO_HOME/notebooks:/py4at" pyalgo:basic`

## Android app: multi-broker positions

A single app lives under **`android-positions-app/`** (package `com.positions.aggregator`). It shows **open positions** from **eToro**, **Interactive Brokers** (Client Portal Web API), **XTB** (xAPI WebSocket), and **Kraken** (signed REST), with:

- **Connector + repository** layout for each broker
- **Summary** card (count, unrealized PnL, approximate notional exposure)
- **Encrypted** credential storage (`SecureConfigStore` + AndroidX Security Crypto), with automatic migration from older plain JSON or legacy encrypted prefs if present

### Build

1. Open **`android-positions-app/`** in Android Studio (SDK 34+).
2. Set `sdk.dir` in `local.properties` or `ANDROID_HOME`.
3. Run `./gradlew :app:assembleDebug` or install from the IDE.

### Broker notes

- **eToro**: Default is **Public API** bearer token against `https://public-api.etoro.com` (`/trading/info/real|demo/pnl`). Optional **partner API** mode uses `x-api-key` + `x-user-key` and your chosen base URL + `/api/v1/trading/info/portfolio`.
- **IBKR**: Uses `/portfolio/accounts` when Account ID is left blank (then pages positions per account). Optional session header/cookie.
- **XTB**: Configurable WebSocket URL (demo `wss://ws.xtb.com/demo` or real `wss://ws.xtb.com/real`).
- **Kraken**: `OpenPositions` with correct `API-Sign` (`SHA256(nonce + postData)`).
