# OpenClaw

OpenClaw now includes a lightweight runtime foundation under `openclaw/`:

- `RuntimeConfig`: environment-driven runtime settings.
- `EventBus`: in-process pub/sub for lifecycle and system events.
- `ServiceContainer`: ordered service registry with startup rollback.
- `OpenClawRuntime`: orchestration for config, services, and lifecycle events.

## Run tests

```bash
python -m unittest discover -s tests -v
```

## Docker Build & Run (existing notebooks environment)

### Build my Python environment image
`cd docker`
`docker build -t pyalgo:basic .`

### Run the container
export ALGO_HOME=/Users/elton/dev/algo-trading

`cd docker`
`docker run -ti -p 8888:8888 -v "$ALGO_HOME/notebooks:/py4at" pyalgo:basic`

