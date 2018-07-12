# ci3 installation in GCE

## Kuberenets cluster configuration

- Create a kubernetes cluster using GCE (Google Cloud Engine)
- Install gcloud cli tool
- Setup credentials for kubectl
- Make sure that kubectl in the right context

```bash
  gcloud init
  gcloud container clusters get-credentials YOUR-PROJECT-NAME
  kubectl config current-context
```

Tiller (server side part of helm) need permissions to perform actions, simpliest
way is to add clusterrolebinding for `system:serviceaccount:kube-system:default`.

```bash
  kubectl create clusterrolebinding --user system:serviceaccount:kube-system:default kube-system-cluster-admin --clusterrole cluster-admin
  helm init
```

If you have few users accessing same cluster do helm installation in different
way:
- [link1](https://github.com/kubernetes/helm/blob/master/docs/securing_installation.md)
- [link2](https://github.com/kubernetes/helm/blob/master/docs/rbac.md)


### Create ci3-bucket

If cache functionallity is needed create a google storage bucket called
`ci3-bucket`

### Setup loadbalancer

External address will be needed for webhooks. Install
[ingress](https://kubernetes.io/docs/concepts/services-networking/ingress/)
nginx for this purpose. nginx-ingress
[allows](https://serverfault.com/questions/877275/nginx-vs-gce-kubernetes-ingress-classes)
to have only one loadbalancer automatically created by gce.

```bash
helm install stable/nginx-ingress -n ingress --set rbac.create=true
helm ls
```

After that for configuring your DNS you can find balancer ip via GCE interface:

![2018-05-29-095655_852x531_scrot](https://user-images.githubusercontent.com/1218615/40642766-b8a620bc-6326-11e8-8914-456138ea77c3.png)

## Configure and deploy ci3

### Setup access for ci3 server

Server part of ci3 need an ability to run agents, which will perform the build.
Both server and agents have to have access to kubernetes api. Simpliest, but not
safest way to give accesses is to create a clusterrolebinding for user
`system:serviceaccount:default:default`

```bash
kubectl create clusterrolebinding --user system:serviceaccount:default:default kube-system-cluster-admin-2 --clusterrole cluster-admin
```

### Setup configs and secrets for ci3

#### Introduction
There are few type of kubernetes objects, which can be used to store secrets and
other useful information, ci3 uses following for secrets:
- Opaque
- kubernetes.io/tls
- kubernetes.io/dockercfg 

following for configurations:
- ConfigMap

There are few ways to
[create](https://kubernetes.io/docs/concepts/configuration/secret/#creating-a-secret-manually)
secrets. Simple and repeatable way is to store secrets locally in yaml files and
create/update them in kubernetes using `kubectl apply -f YOUR-SECRET.yaml`. All
secret values must be base64 encoded. To encode value use `base64` tool:

```bash
echo -n "YOUR-VALUE-HERE" | base64 -w 0
```


#### Getting all the secrets

There is a folder called [configs](./configs), which contains templates of secret and
config files, empty (`PUT-YOUR-VALUE`) fields should be filled according to
information inside file.

In `docker-registry-secret.yaml` and `storage-secret.yaml` files provided
information about obtaining keys for service accounts. Put this files root
folder for ci3 chart.


```bash

kubectl apply -f ./configs/

# TODO: maybe move following command to separate yaml template

kubectl create secret docker-registry gcr-json-key \
        --docker-server=https://eu.gcr.io \
        --docker-username=_json_key \
        --docker-password="$(cat ci3-docker-key.json)" \
        --docker-email=gcr@gserviceaccount.com

kubectl patch serviceaccount default \
        -p '{"imagePullSecrets": [{"name": "gcr-json-key"}]}'
```


### Install ci3

Ajust values.yaml with proper values.

```bash
helm upgrade -i ci3 .
```

### Create a repository resource

Add kubernetes resource called repository and related secret.
Example config can be found [here](./repo-configs/repo.yaml).

### Setup a hook

Open https://github.com/YOUR-PROJECT-HERE/settings/hooks/
create an application/json hook to https://YOUR-CI-DOMAIN/webhook/YOUR-REPO-NAME

## Troubleshooting and helpful commands

In case something goes wrong you can inspect values of objects and inspect debug
information of pods:

```bash
kubectl get ANY-TYPE ANY-OBJECT -o yaml

kubectl get pods
kubectl logs -f YOUR-POD-NAME
```

Login to google container registry with gcloud account to have ability to
push/pull images to/from GCR using local docker (helpful for debugging purpose):

```bash
gcloud auth print-access-token | docker login -u oauth2accesstoken --password-stdin https://eu.gcr.io
```

# PROFIT
# You are perfect!

Enjoy your riding :)
