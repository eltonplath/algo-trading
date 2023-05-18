
## Docker Build & Run

### Build my Python environment image
`cd docker`
`docker build -t pyalgo:basic .`

### Run the container
export ALGO_HOME=/Users/elton/dev/algo-trading

`cd docker`
`docker run -ti -p 8888:8888 -v "$ALGO_HOME/notebooks:/py4at" pyalgo:basic`

