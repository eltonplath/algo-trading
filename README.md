
## Docker Build & Run

### Build my Python environment image
`cd docker`
`docker build -t pyalgo:basic .`

### Run the container
`cd docker`
`docker run -ti -p 8888:8888 -v "/Users/elton/dev/algotrading/py4at:/py4at" pyalgo:basic`

