
## Docker Build & Run

### Build my Python environment image
`cd docker`
`docker build -t pyalgo:basic .`

### Run the container
export ALGO_HOME=/Users/elton/dev/algo-trading

`cd docker`
`docker run -ti -p 8888:8888 -v "$ALGO_HOME/notebooks:/py4at" pyalgo:basic`

## Android positions app (moved)

The multi-broker Android positions viewer now lives in its own repository:

**https://github.com/eltonplath/positions-viewer** (clone: `git@github.com:eltonplath/positions-viewer.git`)

This repository includes **`positions-viewer.bundle`** at the repo root (a `git bundle` of the app’s initial commit). From the directory that contains the file:

```bash
git clone positions-viewer.bundle positions-viewer
cd positions-viewer
git remote add origin git@github.com:eltonplath/positions-viewer.git
git push -u origin main
```

After the push succeeds, you can delete the bundle from this repo if you prefer not to keep a copy here.
