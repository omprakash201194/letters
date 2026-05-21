# Letters — Secret Setup

Two secrets are needed before deploying.

## 1. Firebase service account (`letters-firebase`)

Copy the service account JSON from Kin-Keeper (same Firebase project) or create a new one:

```bash
# Create the secret (plaintext — will be kubesealed)
kubectl create secret generic letters-firebase \
  --from-file=firebase-service-account.json=/path/to/firebase-service-account.json \
  --namespace homelab \
  --dry-run=client -o yaml > /tmp/letters-firebase-secret.yaml

# Seal it
kubeseal --cert ~/HomeLab/sealed-secrets-cert.pem \
  --format yaml < /tmp/letters-firebase-secret.yaml > letters-firebase-sealed-secret.yaml

rm /tmp/letters-firebase-secret.yaml
```

## 2. Database password (`letters-db-secret`)

```bash
kubectl create secret generic letters-db-secret \
  --from-literal=DB_PASSWORD='<your-homelab-postgres-password>' \
  --namespace homelab \
  --dry-run=client -o yaml > /tmp/letters-db-secret.yaml

kubeseal --cert ~/HomeLab/sealed-secrets-cert.pem \
  --format yaml < /tmp/letters-db-secret.yaml > letters-db-sealed-secret.yaml

rm /tmp/letters-db-secret.yaml
```

## 3. Update configmap.yaml

Set `FIREBASE_PROJECT_ID` to your Firebase project ID in `configmap.yaml`.

## 4. Create the letters database schema

The schema is auto-created by Hibernate on first boot (`ddl-auto: update`).
No manual migration needed.
