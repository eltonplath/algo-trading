
## Docker Build & Run

### Build my Python environment image
`cd docker`
`docker build -t pyalgo:basic .`

### Run the container
export ALGO_HOME=/Users/elton/dev/algo-trading

`cd docker`
`docker run -ti -p 8888:8888 -v "$ALGO_HOME/notebooks:/py4at" pyalgo:basic`

## Android apps (two implementations)

The repo contains **two** Android projects that solve the same problem (aggregate positions across eToro, Interactive Brokers, XTB, and Kraken). Pick one to maintain going forward, or keep both if you want the alternate UI/architecture.

### 1) `android-positions-app/` — encrypted credentials + Compose tabs

- Package: `com.positions.aggregator`
- Credentials: **EncryptedSharedPreferences** (AndroidX Security Crypto)
- eToro: **Bearer token** + `x-request-id` against `https://public-api.etoro.com/api/v1/...` (demo vs real path)
- IB / XTB / Kraken: same integration ideas as below; Kraken signing uses `SHA256(nonce + postData)` per Kraken’s spec
- Includes Gradle wrapper: `./gradlew :app:assembleDebug` from `android-positions-app/`

### 2) `android-app/` — “Portfolio Position Hub” (merged from `cursor/android-positions-aggregator-375b`)

An Android app under `android-app/` that aggregates open positions across:

- eToro
- Interactive Brokers (Client Portal API)
- XTB (WebSocket API)
- Kraken

### What it does

- Lets you configure connection credentials/endpoints for all four brokers.
- Fetches broker positions in parallel.
- Normalizes the results into a single model and shows:
  - symbol
  - direction (long/short)
  - quantity
  - average open price
  - mark price (if available)
  - unrealized PnL (if available)
- Provides a summary card (open count, total unrealized PnL, approximate exposure).
- Stores config locally in app internal storage.

### Build

1. Open `android-app/` in Android Studio Hedgehog+ (or newer).
2. Let Gradle sync.
3. Run on emulator/device (API 26+).

Command line (if Android SDK is available):

```bash
cd android-app
./gradlew :app:assembleDebug
```

### Broker-specific notes

- **eToro**: Uses `GET /api/v1/trading/info/portfolio` with required headers (`x-api-key`, `x-user-key`, `x-request-id`).
  - This typically requires eToro partner API access.
- **Interactive Brokers**: Uses Client Portal endpoint:
  - `/portfolio/{accountId}/positions/0`
  - Base URL defaults to `https://localhost:5000/v1/api`.
- **XTB**: Uses websocket flow:
  - `login` then `getTrades(openedOnly=true)`.
- **Kraken**: Uses private endpoint:
  - `/0/private/OpenPositions` with HMAC signature (`API-Key`, `API-Sign`).

### Security note (`android-app/`)

Credentials are persisted in the app's internal files directory for convenience.
For production use, migrate credential storage to Android Keystore + encrypted storage (see `android-positions-app/` for an encrypted-storage approach).

