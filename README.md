# ci3

A minimalistic ci for k8s

## Motivation

k8s provides us with nice platform to run isolated payloads.
ci3 is hacker's ci which could provide CI/CD inside k8s cluster, as well as
ability to run and debug tests localy using agent mode

## Usage

1. [Deploy](./ci3/README.md) ci3 to your k8s cluster
1. Create [Repository](./ci3/repo-configs/repo.yaml) or Organization resource
1. Add [ci3.yaml](./ci3/repo-configs/ci3.yaml) manifest into project

## Getting started

Install local test runner ci3 brew, download jar etc

## Secrets


## License

## Run and deploy

```
kubectl create namespace test
```

Copyright © 2017 niquola aitem

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.

---
