# Letters — Deployment TODO

Step-by-step checklist to get Letters running at `https://letters.homelab.local`.

---

## Prerequisites

- [ ] k3s cluster running (`kubectl get nodes`)
- [ ] Local Docker registry running at `localhost:30500`
- [ ] PostgreSQL running in `homelab` namespace
- [ ] cert-manager + `homelab-ca-issuer` running
- [ ] Sealed Secrets controller running
- [ ] Firebase project exists (can reuse Kin-Keeper's `kin-keeper-7a525`)

---

## Step 1 — Firebase setup

- [ ] Go to [Firebase Console](https://console.firebase.google.com) → your project → Project Settings → Service accounts
- [ ] Click **Generate new private key** → save as `backend/firebase-service-account.json`
- [ ] Enable **Google sign-in**: Authentication → Sign-in method → Google → Enable
- [ ] Add `https://letters.homelab.local` to **Authorized domains**: Authentication → Settings → Authorized domains

---

## Step 2 — Create Sealed Secrets

### 2a. Firebase service account secret

```bash
cd ~/HomeLab/webapps/letters

kubectl create secret generic letters-firebase \
  --from-file=firebase-service-account.json=backend/firebase-service-account.json \
  --namespace homelab \
  --dry-run=client -o yaml > /tmp/letters-firebase-secret.yaml

kubeseal --cert ~/HomeLab/sealed-secrets-cert.pem \
  --format yaml < /tmp/letters-firebase-secret.yaml \
  > k8s/letters-firebase-sealed-secret.yaml

rm /tmp/letters-firebase-secret.yaml
rm backend/firebase-service-account.json   # don't leave plaintext around
```

- [ ] `k8s/letters-firebase-sealed-secret.yaml` created

### 2b. Database password secret

```bash
# Use the same password as the rest of the homelab apps
kubectl create secret generic letters-db-secret \
  --from-literal=DB_PASSWORD='<postgres-password>' \
  --namespace homelab \
  --dry-run=client -o yaml > /tmp/letters-db-secret.yaml

kubeseal --cert ~/HomeLab/sealed-secrets-cert.pem \
  --format yaml < /tmp/letters-db-secret.yaml \
  > k8s/letters-db-sealed-secret.yaml

rm /tmp/letters-db-secret.yaml
```

- [ ] `k8s/letters-db-sealed-secret.yaml` created

---

## Step 3 — Update configmap

Edit `k8s/configmap.yaml` and fill in `FIREBASE_PROJECT_ID`:

```yaml
FIREBASE_PROJECT_ID: "kin-keeper-7a525"   # ← set this
```

- [ ] `FIREBASE_PROJECT_ID` set in `k8s/configmap.yaml`

---

## Step 4 — Get Firebase web credentials

- [ ] Go to Firebase Console → Project Settings → General → Your apps
- [ ] Add a **Web app** (or use existing) → copy the config values:
  - `apiKey`
  - `authDomain`
  - `projectId`

These are needed at build time as `VITE_FIREBASE_*` env vars (not secrets — they're public client-side values).

---

## Step 5 — Build and push Docker images

```bash
cd ~/HomeLab/webapps/letters

export VITE_FIREBASE_API_KEY="AIza..."
export VITE_FIREBASE_AUTH_DOMAIN="your-project.firebaseapp.com"
export VITE_FIREBASE_PROJECT_ID="your-project-id"

./build-and-push.sh 1.0.0
```

- [ ] `localhost:30500/homelab/letters-backend:1.0.0` pushed
- [ ] `localhost:30500/homelab/letters-frontend:1.0.0` pushed

---

## Step 6 — Apply k8s manifests

```bash
cd ~/HomeLab/webapps/letters

# Apply sealed secrets first so the deployment can reference them
kubectl apply -f k8s/letters-firebase-sealed-secret.yaml
kubectl apply -f k8s/letters-db-sealed-secret.yaml

# Apply everything else
kubectl apply -f k8s/
```

- [ ] Secrets applied
- [ ] ConfigMap applied
- [ ] Deployments applied
- [ ] Services applied
- [ ] Ingress applied

### Verify pods come up

```bash
kubectl get pods -n homelab -l app=letters-backend
kubectl get pods -n homelab -l app=letters-frontend

# Watch logs if a pod doesn't start
kubectl logs -n homelab -l app=letters-backend -f
```

- [ ] `letters-backend` pod is `Running` and `1/1 Ready`
- [ ] `letters-frontend` pod is `Running` and `1/1 Ready`

---

## Step 7 — Add DNS entry on client machines

Add to `/etc/hosts` on every machine you want to access the app from:

```
100.76.108.123  letters.homelab.local
```

On the homelab server itself it's not needed (nginx ingress listens on the node IP).

- [ ] `/etc/hosts` updated on your dev machine
- [ ] `/etc/hosts` updated on any other clients (phone via Tailscale uses AdGuard DNS — add there too if needed)

---

## Step 8 — Smoke test

- [ ] Open `https://letters.homelab.local` — should show the Login page
- [ ] Sign in with Google — should redirect to Scenes list
- [ ] Create a new scene — add 2 characters, write a few messages
- [ ] Hit **Preview** — typing indicator + bubble animations play
- [ ] Hit **💾 Save** — scene appears in the list on the home page
- [ ] Reload the page — scene still there (confirms DB persistence works)
- [ ] Delete the scene from the list

---

## Step 9 — Commit the sealed secrets

```bash
cd ~/HomeLab/webapps/letters
git add k8s/letters-firebase-sealed-secret.yaml k8s/letters-db-sealed-secret.yaml k8s/configmap.yaml
git commit -m "chore: add sealed secrets and fill firebase project id"
git push
```

- [ ] Sealed secrets committed to git

---

## Optional — Add to Homepage dashboard

Add Letters to `~/HomeLab/homepage/k8s/configmap.yaml` under services:

```yaml
- Letters:
    href: https://letters.homelab.local
    description: WhatsApp-style chat scene builder
    icon: mdi-message-text
```

- [ ] Homepage configmap updated
- [ ] `kubectl apply -f ~/HomeLab/homepage/k8s/`

---

## Troubleshooting

| Symptom | Check |
|---|---|
| Backend pod CrashLoopBackOff | `kubectl logs` — usually missing secret or wrong Firebase project ID |
| `401 Unauthorized` on API calls | Firebase token expired or wrong `authDomain` in frontend build |
| White screen / JS error | Open browser devtools — likely `VITE_FIREBASE_*` env var missing at build time |
| TLS cert not issued | `kubectl describe certificate letters-tls -n homelab` — cert-manager logs |
| Can't connect to DB | Verify `DB_PASSWORD` secret and that `postgres.homelab.svc.cluster.local` is reachable |
