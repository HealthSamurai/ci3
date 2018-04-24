# ci3 installation in GCE

All changes should be done on files in configs folder

0. Prepare cluster
``` bash
# BAD PRACTICE
# TODO Write proper ACL
kubectl create clusterrolebinding root-cluster-admin-binding --clusterrole=cluster-admin --user=default
kubectl create clusterrolebinding --user system:serviceaccount:kube-system:default kube-system-cluster-admin --clusterrole cluster-admin

helm install stable/nginx-ingress -n ingress --set rbac.create=true
```
1. TLS Secret
   1. In case of https ingress - change secrets name in  ingress section of values.yaml
2. Github access token
   1. Get token from https://github.com/settings/tokens with read-only access
   2. echo -n "token" | base64
   3. put encoded secret to github.yaml
3. repo.yaml
   1. Change name and URL
4. ci3-configmap
   1. change BASE_URL
5. ci3-secret
   1. GITHUB_TOKEN change to issued earlier
   2. TELEGRAM_TOKEN - create new bot trough https://web.telegram.org/#/im?p=@BotFather , obtain token and base64 encrypt it
   3. TELEGRAM_CHATID - get chatid from chat where build notifications should be sent. @RawDataBot may help
   4. DOCKER_* We will use GCE Container Storage so this options should not be changed
6. storage-secret
   1. boto - change default_project_id in storage-secret-boto and base64 encode it
   2. account
      1. Go to GCE Console > IAM & Admin > Service accounts
      2. Create new account with role Storage > Storage Admin
      3. Select Furnish a new private key and create account
      4. Encode obtained key and put it to account field
7. docker-registry-secret
   1. key
      1. Go to GCE Console > IAM & Admin > Service accounts
      2. Create new account with roles Cloud Container [Builder, Editor, Viewer]
         If role is not available in dropdown create custom role
      3. Select Furnish a new private key and create account
      4. Encode obtained key and put it to key field
8. `kubectl apply -f ci3/configs`
9. Ajust values.yaml for helm chart
10. `helm upgrade ci3 ci3`
11. Allow access to gce docker registry inside cluster
``` bash
kubectl create secret docker-registry gcr-json-key \
        --docker-server=https://eu.gcr.io \
        --docker-username=_json_key \
        --docker-password="$(cat docker-registry-key.json)" \
        --docker-email=gcr@gserviceaccount.com

kubectl patch serviceaccount default \
        -p '{"imagePullSecrets": [{"name": "gcr-json-key"}]}'
```
